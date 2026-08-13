using System.Reflection;
using System.Text.RegularExpressions;
using Microsoft.EntityFrameworkCore;

namespace Calciolari.DataHub.Persistence;

public sealed record SqlMigration(string Version, int Order, string Description, string Sql);

public static class SqlMigrator
{
    private static readonly Regex ResourceName = new(@"V(\d+)__([^.]+)\.sql$", RegexOptions.Compiled);

    public static void Apply(DataHubDbContext db) => Apply(db, typeof(SqlMigrator).Assembly);

    internal static void Apply(DataHubDbContext db, Assembly assembly)
    {
        ArgumentNullException.ThrowIfNull(db);
        ArgumentNullException.ThrowIfNull(assembly);
        db.Database.OpenConnection();
        var conn = db.Database.GetDbConnection();
        using (var history = conn.CreateCommand())
        {
            history.CommandText = """
                CREATE TABLE IF NOT EXISTS schema_history (
                  version TEXT PRIMARY KEY,
                  description TEXT NOT NULL,
                  applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
                )
                """;
            history.ExecuteNonQuery();
        }

        var applied = ReadApplied(conn);
        var migrations = Discover(assembly);
        var rawExists = TableExists(conn, "raw_artifact");
        if (rawExists && !applied.Contains("V1"))
        {
            InsertHistory(conn, "V1", "import_and_canonical (baseline)");
            applied.Add("V1");
        }

        foreach (var migration in migrations)
        {
            if (applied.Contains(migration.Version))
            {
                continue;
            }

            using var cmd = conn.CreateCommand();
            cmd.CommandText = migration.Sql;
            cmd.ExecuteNonQuery();
            InsertHistory(conn, migration.Version, migration.Description);
            applied.Add(migration.Version);
        }
    }

    internal static string LoadSql() => LoadSql(typeof(SqlMigrator).Assembly);

    internal static string LoadSql(Assembly assembly)
    {
        var v1 = Discover(assembly).FirstOrDefault(m => m.Version == "V1");
        if (v1 is null)
        {
            throw new InvalidOperationException("Embedded SQL migration V1__import_and_canonical.sql is missing.");
        }

        return v1.Sql;
    }

    internal static IReadOnlyList<SqlMigration> Discover(Assembly assembly)
    {
        var list = new List<SqlMigration>();
        foreach (var name in assembly.GetManifestResourceNames())
        {
            var match = ResourceName.Match(name);
            if (!match.Success)
            {
                continue;
            }

            using var stream = assembly.GetManifestResourceStream(name)!;
            using var reader = new StreamReader(stream);
            var order = int.Parse(match.Groups[1].Value, System.Globalization.CultureInfo.InvariantCulture);
            list.Add(new SqlMigration("V" + order, order, match.Groups[2].Value, reader.ReadToEnd()));
        }

        return list.OrderBy(m => m.Order).ToList();
    }

    private static HashSet<string> ReadApplied(System.Data.Common.DbConnection conn)
    {
        var applied = new HashSet<string>(StringComparer.Ordinal);
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "SELECT version FROM schema_history";
        using var reader = cmd.ExecuteReader();
        while (reader.Read())
        {
            applied.Add(reader.GetString(0));
        }

        return applied;
    }

    private static bool TableExists(System.Data.Common.DbConnection conn, string table)
    {
        using var check = conn.CreateCommand();
        check.CommandText =
            "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = @t)";
        var p = check.CreateParameter();
        p.ParameterName = "t";
        p.Value = table;
        check.Parameters.Add(p);
        return true.Equals(check.ExecuteScalar());
    }

    private static void InsertHistory(System.Data.Common.DbConnection conn, string version, string description)
    {
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "INSERT INTO schema_history (version, description) VALUES (@v, @d)";
        var v = cmd.CreateParameter();
        v.ParameterName = "v";
        v.Value = version;
        cmd.Parameters.Add(v);
        var d = cmd.CreateParameter();
        d.ParameterName = "d";
        d.Value = description;
        cmd.Parameters.Add(d);
        cmd.ExecuteNonQuery();
    }
}
