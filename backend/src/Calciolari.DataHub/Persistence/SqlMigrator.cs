using System.Reflection;
using Microsoft.EntityFrameworkCore;

namespace Calciolari.DataHub.Persistence;

public static class SqlMigrator
{
    public static void Apply(DataHubDbContext db)
    {
        ArgumentNullException.ThrowIfNull(db);
        db.Database.OpenConnection();
        var conn = db.Database.GetDbConnection();
        using var check = conn.CreateCommand();
        check.CommandText = "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'raw_artifact')";
        var exists = check.ExecuteScalar();
        if (true.Equals(exists))
        {
            return;
        }

        using var migrate = conn.CreateCommand();
        migrate.CommandText = LoadSql();
        migrate.ExecuteNonQuery();
    }

    internal static string LoadSql() => LoadSql(typeof(SqlMigrator).Assembly);

    internal static string LoadSql(Assembly assembly)
    {
        var name = assembly.GetManifestResourceNames()
            .FirstOrDefault(n => n.EndsWith("V1__import_and_canonical.sql", StringComparison.Ordinal))
            ?? "__missing__.sql";
        using var stream = assembly.GetManifestResourceStream(name);
        if (stream is null)
        {
            throw new InvalidOperationException("Embedded SQL migration V1__import_and_canonical.sql is missing.");
        }

        using var reader = new StreamReader(stream);
        return reader.ReadToEnd();
    }
}
