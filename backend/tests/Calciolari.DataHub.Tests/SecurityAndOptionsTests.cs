using Calciolari.DataHub.Shared.Security;
using Calciolari.DataHub.Tests.Support;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;

namespace Calciolari.DataHub.Tests;

public sealed class SecurityAndOptionsTests
{
    [Fact]
    public void ParseUser_valid_and_invalid()
    {
        var user = BasicAuthenticationHandler.ParseUser("admin:secret:ADMIN|VIEWER");
        Assert.Equal("admin", user.Username);
        Assert.Equal("secret", user.Password);
        Assert.Equal(["ADMIN", "VIEWER"], user.Roles);
        Assert.Throws<ArgumentException>(() => BasicAuthenticationHandler.ParseUser("only-two:parts"));
        Assert.Throws<ArgumentException>(() => BasicAuthenticationHandler.ParseUser(" :x:ADMIN"));
        Assert.Throws<ArgumentException>(() => BasicAuthenticationHandler.ParseUser("u: :ADMIN"));
        Assert.Throws<ArgumentException>(() => BasicAuthenticationHandler.ParseUser("u:p:"));
    }

    [Fact]
    public void ParseUsers_from_options()
    {
        var options = new DataHubOptions { SecurityUsers = "a:b:VIEWER, c:d:ADMIN" };
        var users = BasicAuthenticationHandler.ParseUsers(options);
        Assert.Equal(2, users.Count);
        Assert.Empty(BasicAuthenticationHandler.ParseUsers(new DataHubOptions()));
        Assert.Empty(new DataHubOptions { SecurityUsers = "  " }.UserEntries());
        Assert.Empty(new DataHubOptions { CorsAllowedOrigins = "  " }.OriginList());
        Assert.Equal(["http://localhost:5173"], new DataHubOptions { CorsAllowedOrigins = "http://localhost:5173" }.OriginList());
        Assert.True(BasicAuthenticationHandler.FixedTimeEquals("secret", "secret"));
        Assert.False(BasicAuthenticationHandler.FixedTimeEquals("secret", "wrong"));
        Assert.False(BasicAuthenticationHandler.FixedTimeEquals("ab", "abc"));
    }

    [Fact]
    public void Options_validate_and_spring_jdbc()
    {
        var options = new DataHubOptions { SecurityRequireEnabled = true, SecurityEnabled = false };
        Assert.Throws<InvalidOperationException>(() => options.ValidateOrThrow("Production"));
        options.SecurityEnabled = true;
        Assert.Throws<InvalidOperationException>(() => options.ValidateOrThrow("Production"));
        options.SecurityUsers = "a:b:ADMIN";
        options.ValidateOrThrow("Production");

        Assert.Null(AppHost.FromSpringJdbc(null, null, null));
        Assert.Null(AppHost.FromSpringJdbc("mysql://x", "u", "p"));
        Assert.Equal(
            "Host=localhost;Port=5432;Database=datahub;Username=datahub;Password=change-me",
            AppHost.FromSpringJdbc("jdbc:postgresql://localhost/datahub", null, null));
        Assert.Equal(
            "Host=solo;Port=5432;Database=datahub;Username=u;Password=p",
            AppHost.FromSpringJdbc("jdbc:postgresql://solo", "u", "p"));
        Assert.Equal(
            "Host=db;Port=5433;Database=x;Username=u;Password=p",
            AppHost.FromSpringJdbc("jdbc:postgresql://db:5433/x?ssl=true", "u", "p"));
    }

    [Fact]
    public void BindOptions_reads_configuration()
    {
        var previous = new Dictionary<string, string?>
        {
            ["DATAHUB_CONNECTION_STRING"] = Environment.GetEnvironmentVariable("DATAHUB_CONNECTION_STRING"),
            ["DATAHUB_RAW_STORAGE_ROOT"] = Environment.GetEnvironmentVariable("DATAHUB_RAW_STORAGE_ROOT"),
            ["DATAHUB_SECURITY_ENABLED"] = Environment.GetEnvironmentVariable("DATAHUB_SECURITY_ENABLED"),
            ["DATAHUB_SECURITY_USERS"] = Environment.GetEnvironmentVariable("DATAHUB_SECURITY_USERS"),
            ["DATAHUB_CORS_ALLOWED_ORIGINS"] = Environment.GetEnvironmentVariable("DATAHUB_CORS_ALLOWED_ORIGINS"),
            ["DATAHUB_IMPORTS_MAX_FILES"] = Environment.GetEnvironmentVariable("DATAHUB_IMPORTS_MAX_FILES"),
            ["DATAHUB_IMPORTS_MAX_FILE_BYTES"] = Environment.GetEnvironmentVariable("DATAHUB_IMPORTS_MAX_FILE_BYTES"),
            ["DATAHUB_PARSER_MAX_BYTES"] = Environment.GetEnvironmentVariable("DATAHUB_PARSER_MAX_BYTES"),
            ["DATAHUB_DEBUG_ENABLED"] = Environment.GetEnvironmentVariable("DATAHUB_DEBUG_ENABLED"),
            ["SPRING_DATASOURCE_URL"] = Environment.GetEnvironmentVariable("SPRING_DATASOURCE_URL")
        };
        try
        {
            foreach (var key in previous.Keys)
            {
                Environment.SetEnvironmentVariable(key, null);
            }

            var config = new ConfigurationBuilder().AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["ConnectionStrings:DefaultConnection"] = "Host=h;Database=d;Username=u;Password=p",
                ["DataHub:RawStorageRoot"] = "/tmp/raw",
                ["DataHub:MaxFiles"] = "3"
            }).Build();
            var bound = AppHost.BindOptions(config, "Development");
            Assert.Contains("Host=h", bound.ConnectionString);
            Assert.Equal("/tmp/raw", bound.RawStorageRoot);
            Assert.False(bound.SecurityEnabled);
            Assert.False(bound.DebugEnabled);

            var debugConfig = new ConfigurationBuilder().AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["ConnectionStrings:DefaultConnection"] = "Host=h;Database=d;Username=u;Password=p",
                ["DataHub:DebugEnabled"] = "true"
            }).Build();
            Assert.True(AppHost.BindOptions(debugConfig, "Development").DebugEnabled);
            Assert.False(AppHost.BindOptions(debugConfig, "Production").DebugEnabled);

            var prod = AppHost.BindOptions(config, "Production");
            Assert.True(prod.SecurityEnabled);
            Assert.True(prod.SecurityRequireEnabled);

            var prodCorsConfig = new ConfigurationBuilder().AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["ConnectionStrings:DefaultConnection"] = "Host=h;Database=d;Username=u;Password=p",
                ["DataHub:CorsAllowedOrigins"] = "https://app.example"
            }).Build();
            var prodCors = AppHost.BindOptions(prodCorsConfig, "Production");
            Assert.Equal("https://app.example", prodCors.CorsAllowedOrigins);

            Environment.SetEnvironmentVariable("DATAHUB_CONNECTION_STRING", "Host=env");
            Environment.SetEnvironmentVariable("DATAHUB_RAW_STORAGE_ROOT", "/env/raw");
            Environment.SetEnvironmentVariable("DATAHUB_IMPORTS_MAX_FILES", "9");
            Environment.SetEnvironmentVariable("DATAHUB_IMPORTS_MAX_FILE_BYTES", "12");
            Environment.SetEnvironmentVariable("DATAHUB_PARSER_MAX_BYTES", "13");
            Environment.SetEnvironmentVariable("DATAHUB_SECURITY_ENABLED", "true");
            Environment.SetEnvironmentVariable("DATAHUB_SECURITY_USERS", "u:p:ADMIN");
            Environment.SetEnvironmentVariable("DATAHUB_CORS_ALLOWED_ORIGINS", "http://x");
            Environment.SetEnvironmentVariable("DATAHUB_DEBUG_ENABLED", "true");
            var fromEnv = AppHost.BindOptions(config, "Development");
            Assert.Equal("Host=env", fromEnv.ConnectionString);
            Assert.Equal("/env/raw", fromEnv.RawStorageRoot);
            Assert.Equal(9, fromEnv.MaxFiles);
            Assert.Equal(12, fromEnv.MaxFileBytes);
            Assert.Equal(13, fromEnv.ParserMaxBytes);
            Assert.True(fromEnv.SecurityEnabled);
            Assert.Equal("u:p:ADMIN", fromEnv.SecurityUsers);
            Assert.Equal("http://x", fromEnv.CorsAllowedOrigins);
            Assert.True(fromEnv.DebugEnabled);

            Environment.SetEnvironmentVariable("DATAHUB_DEBUG_ENABLED", "false");
            Assert.False(AppHost.BindOptions(config, "Development").DebugEnabled);
            Environment.SetEnvironmentVariable("DATAHUB_DEBUG_ENABLED", "true");
            Assert.False(AppHost.BindOptions(config, "Production").DebugEnabled);

            Environment.SetEnvironmentVariable("DATAHUB_CONNECTION_STRING", null);
            Environment.SetEnvironmentVariable("SPRING_DATASOURCE_URL", "jdbc:postgresql://spring:5432/db");
            Environment.SetEnvironmentVariable("SPRING_DATASOURCE_USERNAME", "su");
            Environment.SetEnvironmentVariable("SPRING_DATASOURCE_PASSWORD", "sp");
            var fromSpring = AppHost.BindOptions(new ConfigurationBuilder().Build(), "Development");
            Assert.Contains("Host=spring", fromSpring.ConnectionString);
        }
        finally
        {
            foreach (var pair in previous)
            {
                Environment.SetEnvironmentVariable(pair.Key, pair.Value);
            }
        }
    }

    [Fact]
    public void SqlMigrator_load_sql()
    {
        var sql = Calciolari.DataHub.Persistence.SqlMigrator.LoadSql();
        Assert.Contains("CREATE TABLE raw_artifact", sql);
        Assert.Throws<InvalidOperationException>(() =>
            Calciolari.DataHub.Persistence.SqlMigrator.LoadSql(typeof(string).Assembly));

        using var db = Support.TestDb.Open();
        db.Database.ExecuteSqlRaw("""
            DROP TABLE IF EXISTS sale_item, sale, product, validation_result, parsed_movement,
              artifact_publication, import_file, parse_attempt, import_job, raw_artifact, schema_history CASCADE
            """);
        Calciolari.DataHub.Persistence.SqlMigrator.Apply(db);
        Calciolari.DataHub.Persistence.SqlMigrator.Apply(db);
        Assert.Empty(Calciolari.DataHub.Persistence.SqlMigrator.Discover(typeof(string).Assembly));
        Assert.Contains(Calciolari.DataHub.Persistence.SqlMigrator.Discover(typeof(Calciolari.DataHub.Persistence.SqlMigrator).Assembly),
            m => m.Version == "V1");
        db.Database.ExecuteSqlRaw("DELETE FROM schema_history");
        Calciolari.DataHub.Persistence.SqlMigrator.Apply(db);
        Calciolari.DataHub.Persistence.SqlMigrator.Apply(db, typeof(string).Assembly);
    }

    [Fact]
    public void Ingestion_helpers()
    {
        Assert.Equal("IMPORTED", Calciolari.DataHub.Imports.Application.ImportIngestionService.MapFileStatus("VALID"));
        Assert.Equal("WARNING", Calciolari.DataHub.Imports.Application.ImportIngestionService.MapFileStatus("WARNING"));
        Assert.Equal("INVALID", Calciolari.DataHub.Imports.Application.ImportIngestionService.MapFileStatus("INVALID"));
        Assert.Equal("FAILED", Calciolari.DataHub.Imports.Application.ImportIngestionService.MapFileStatus("FAILED"));

        var now = DateTimeOffset.UtcNow;
        Assert.False(Calciolari.DataHub.Imports.Application.ImportIngestionService.HasActiveLease(null));
        var idle = new Calciolari.DataHub.Persistence.Entities.ParseAttemptEntity(
            Guid.NewGuid(), Guid.NewGuid(), "p", "v", "VALID", 1)
        {
            LeaseUntil = now.AddMinutes(5)
        };
        Assert.False(Calciolari.DataHub.Imports.Application.ImportIngestionService.HasActiveLease(idle));
        idle.Status = "PROCESSING";
        idle.LeaseUntil = null;
        Assert.False(Calciolari.DataHub.Imports.Application.ImportIngestionService.HasActiveLease(idle));
        idle.LeaseUntil = now.AddHours(-1);
        Assert.False(Calciolari.DataHub.Imports.Application.ImportIngestionService.HasActiveLease(idle));
        idle.LeaseUntil = now.AddMinutes(5);
        Assert.True(Calciolari.DataHub.Imports.Application.ImportIngestionService.HasActiveLease(idle));
        idle.Status = "PENDING";
        Assert.True(Calciolari.DataHub.Imports.Application.ImportIngestionService.HasActiveLease(idle));

        var info = Calciolari.DataHub.Imports.Application.ImportIngestionService.ToValidationEntity(
            Guid.NewGuid(),
            new Calciolari.DataHub.Imports.Domain.Parser.ParseIssue(
                "SOURCE_QUANTITY_MATCH",
                Calciolari.DataHub.Imports.Domain.Parser.IssueSeverity.Info,
                Calciolari.DataHub.Imports.Domain.Parser.IssueStage.Validation,
                Calciolari.DataHub.Imports.Domain.Parser.SourceLocator.Empty,
                "sourceValue=1 calculatedValue=1 difference=0 tolerance=0.001 ruleVersion=v1 extra=1"));
        Assert.Equal("VALID", info.Status);
        Assert.Equal(1m, info.SourceValue);
        Assert.Equal("v1", info.RuleVersion);

        var warn = Calciolari.DataHub.Imports.Application.ImportIngestionService.ToValidationEntity(
            Guid.NewGuid(),
            new Calciolari.DataHub.Imports.Domain.Parser.ParseIssue(
                "X", Calciolari.DataHub.Imports.Domain.Parser.IssueSeverity.Warning,
                Calciolari.DataHub.Imports.Domain.Parser.IssueStage.Validation,
                new Calciolari.DataHub.Imports.Domain.Parser.SourceLocator(1, 2, null, "d"),
                "no-kv"));
        Assert.Equal("WARNING", warn.Status);

        var invalid = Calciolari.DataHub.Imports.Application.ImportIngestionService.ToValidationEntity(
            Guid.NewGuid(),
            new Calciolari.DataHub.Imports.Domain.Parser.ParseIssue(
                "X", Calciolari.DataHub.Imports.Domain.Parser.IssueSeverity.Error,
                Calciolari.DataHub.Imports.Domain.Parser.IssueStage.Validation,
                Calciolari.DataHub.Imports.Domain.Parser.SourceLocator.Empty,
                "x"));
        Assert.Equal("INVALID", invalid.Status);

        var fatal = Calciolari.DataHub.Imports.Application.ImportIngestionService.ToValidationEntity(
            Guid.NewGuid(),
            new Calciolari.DataHub.Imports.Domain.Parser.ParseIssue(
                "X", Calciolari.DataHub.Imports.Domain.Parser.IssueSeverity.Fatal,
                Calciolari.DataHub.Imports.Domain.Parser.IssueStage.Validation,
                Calciolari.DataHub.Imports.Domain.Parser.SourceLocator.Empty,
                "x"));
        Assert.Equal("INVALID", fatal.Status);

        var unknown = Calciolari.DataHub.Imports.Application.ImportIngestionService.ToValidationEntity(
            Guid.NewGuid(),
            new Calciolari.DataHub.Imports.Domain.Parser.ParseIssue(
                "X", (Calciolari.DataHub.Imports.Domain.Parser.IssueSeverity)99,
                Calciolari.DataHub.Imports.Domain.Parser.IssueStage.Validation,
                Calciolari.DataHub.Imports.Domain.Parser.SourceLocator.Empty,
                "x"));
        Assert.Equal("INVALID", unknown.Status);

        using var stream = new MemoryStream("abc"u8.ToArray());
        var spool = Calciolari.DataHub.Imports.Application.ImportIngestionService.SpoolAndHash(stream);
        Assert.Equal(3, spool.ByteSize);
        Assert.Equal(64, spool.Sha256.Length);
        Assert.True(File.Exists(spool.TempPath));
        File.Delete(spool.TempPath);
        using var boom = new ThrowingReadStream();
        Assert.Throws<IOException>(() =>
            Calciolari.DataHub.Imports.Application.ImportIngestionService.SpoolAndHash(boom));
    }

    private sealed class ThrowingReadStream : Stream
    {
        public override bool CanRead => true;
        public override bool CanSeek => false;
        public override bool CanWrite => false;
        public override long Length => 1;
        public override long Position { get; set; }
        public override void Flush() { }
        public override int Read(byte[] buffer, int offset, int count) => throw new IOException("read");
        public override long Seek(long offset, SeekOrigin origin) => throw new NotSupportedException();
        public override void SetLength(long value) => throw new NotSupportedException();
        public override void Write(byte[] buffer, int offset, int count) => throw new NotSupportedException();
    }

    [Fact]
    public void Sale_bounds_and_product_blank()
    {
        Assert.Equal(new DateTime(1, 1, 1), Calciolari.DataHub.Sales.Application.SaleQueryService.BoundFrom(null));
        Assert.Equal(new DateTime(2020, 1, 1), Calciolari.DataHub.Sales.Application.SaleQueryService.BoundFrom(new DateTime(2020, 1, 1)));
        Assert.Equal(new DateTime(9999, 12, 31, 23, 59, 59), Calciolari.DataHub.Sales.Application.SaleQueryService.BoundTo(null));
        Assert.Equal(new DateTime(2021, 2, 3), Calciolari.DataHub.Sales.Application.SaleQueryService.BoundTo(new DateTime(2021, 2, 3)));
        Assert.Null(Calciolari.DataHub.Catalog.Application.ProductQueryService.BlankToNull(null));
        Assert.Null(Calciolari.DataHub.Catalog.Application.ProductQueryService.BlankToNull("  "));
        Assert.Equal("x", Calciolari.DataHub.Catalog.Application.ProductQueryService.BlankToNull(" x "));
    }

    [Fact]
    public void AppHost_create_invokes_configure()
    {
        var previous = Environment.GetEnvironmentVariable("DATAHUB_RAW_STORAGE_ROOT");
        try
        {
            Environment.SetEnvironmentVariable("ASPNETCORE_ENVIRONMENT", "Development");
            Environment.SetEnvironmentVariable("DATAHUB_CONNECTION_STRING", TestDb.ConnectionString);
            Environment.SetEnvironmentVariable("DATAHUB_RAW_STORAGE_ROOT", Directory.CreateTempSubdirectory("cfg").FullName);
            Environment.SetEnvironmentVariable("DATAHUB_SECURITY_ENABLED", "false");
            var called = false;
            using var app = AppHost.Create(["--urls=http://127.0.0.1:0"], _ => called = true);
            Assert.True(called);
        }
        finally
        {
            Environment.SetEnvironmentVariable("DATAHUB_RAW_STORAGE_ROOT", previous);
        }
    }

    [Fact]
    public void FilenameHintsJson_roundtrip_and_fallback()
    {
        var hints = new Calciolari.DataHub.Imports.Domain.Hints.FilenameHintsParser().Parse("AUDITORIA 01_07-20_07.QRP");
        var json = Calciolari.DataHub.Imports.Application.FilenameHintsJson.Write(hints);
        Assert.Contains("periodHint", json);
        Assert.Contains("INFERRED_DATA", json);
        Assert.Contains("productCodeHint", Calciolari.DataHub.Imports.Application.FilenameHintsJson.Write(
            new Calciolari.DataHub.Imports.Domain.Hints.FilenameHintsParser().Parse("AUDITORIA 41, 01_07-20_07.QRP")));
        Assert.NotNull(Calciolari.DataHub.Imports.Application.FilenameHintsJson.Read(json));
        Assert.Null(Calciolari.DataHub.Imports.Application.FilenameHintsJson.Read(null));
        Assert.Equal("not-json", Calciolari.DataHub.Imports.Application.FilenameHintsJson.Read("not-json"));
        var empty = Calciolari.DataHub.Imports.Application.FilenameHintsJson.Write(
            Calciolari.DataHub.Imports.Domain.Hints.FilenameHints.Empty("AUDITORIA.QRP"));
        Assert.Contains("originalFilename", empty);
        var single = new Calciolari.DataHub.Imports.Domain.Hints.FilenameHintsParser().Parse("relatorio_20_07.QRP");
        Assert.Contains("singleDateHint", Calciolari.DataHub.Imports.Application.FilenameHintsJson.Write(single));
        var previous = Calciolari.DataHub.Imports.Application.FilenameHintsJson.Serialize;
        try
        {
            Calciolari.DataHub.Imports.Application.FilenameHintsJson.Serialize = (_, _) => throw new InvalidOperationException("json");
            var fallback = Calciolari.DataHub.Imports.Application.FilenameHintsJson.Write(
                Calciolari.DataHub.Imports.Domain.Hints.FilenameHints.Empty("say \"hi\".QRP"));
            Assert.Contains("originalFilename", fallback);
            Assert.Contains("\\\"", fallback);
        }
        finally
        {
            Calciolari.DataHub.Imports.Application.FilenameHintsJson.Serialize = previous;
        }
    }
}
