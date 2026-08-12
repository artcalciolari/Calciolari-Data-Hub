using Calciolari.DataHub.Persistence;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Diagnostics;

namespace Calciolari.DataHub.Tests.Support;

internal static class TestDb
{
    public const string ConnectionString =
        "Host=127.0.0.1;Port=5432;Database=datahub;Username=datahub;Password=change-me";

    public static DataHubDbContext Open(params IInterceptor[] interceptors)
    {
        var builder = new DbContextOptionsBuilder<DataHubDbContext>().UseNpgsql(ConnectionString);
        if (interceptors.Length > 0)
        {
            builder.AddInterceptors(interceptors);
        }

        var db = new DataHubDbContext(builder.Options);
        SqlMigrator.Apply(db);
        return db;
    }

    public static void Truncate(DataHubDbContext db)
    {
        db.Database.ExecuteSqlRaw("""
            TRUNCATE TABLE
              sale_item, sale, product, validation_result, parsed_movement,
              artifact_publication, import_file, parse_attempt, import_job, raw_artifact
            RESTART IDENTITY CASCADE
            """);
        db.ChangeTracker.Clear();
    }
}
