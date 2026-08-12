using Calciolari.DataHub.Persistence.Entities;
using Microsoft.EntityFrameworkCore;

namespace Calciolari.DataHub.Persistence;

public sealed class DataHubDbContext : DbContext
{
    public DataHubDbContext(DbContextOptions<DataHubDbContext> options)
        : base(options)
    {
    }

    public DbSet<RawArtifactEntity> RawArtifacts => Set<RawArtifactEntity>();
    public DbSet<ImportJobEntity> ImportJobs => Set<ImportJobEntity>();
    public DbSet<ParseAttemptEntity> ParseAttempts => Set<ParseAttemptEntity>();
    public DbSet<ImportFileEntity> ImportFiles => Set<ImportFileEntity>();
    public DbSet<ArtifactPublicationEntity> ArtifactPublications => Set<ArtifactPublicationEntity>();
    public DbSet<ValidationResultEntity> ValidationResults => Set<ValidationResultEntity>();
    public DbSet<ParsedMovementEntity> ParsedMovements => Set<ParsedMovementEntity>();
    public DbSet<ProductEntity> Products => Set<ProductEntity>();
    public DbSet<SaleEntity> Sales => Set<SaleEntity>();
    public DbSet<SaleItemEntity> SaleItems => Set<SaleItemEntity>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<RawArtifactEntity>(e =>
        {
            e.ToTable("raw_artifact");
            e.HasKey(x => x.Id);
            e.Property(x => x.Id).HasColumnName("id");
            e.Property(x => x.Sha256).HasColumnName("sha256").HasMaxLength(64).IsRequired();
            e.Property(x => x.ByteSize).HasColumnName("byte_size");
            e.Property(x => x.StorageKey).HasColumnName("storage_key").IsRequired();
            e.Property(x => x.DetectedType).HasColumnName("detected_type");
            e.Property(x => x.CreatedAt).HasColumnName("created_at");
        });

        modelBuilder.Entity<ImportJobEntity>(e =>
        {
            e.ToTable("import_job");
            e.HasKey(x => x.Id);
            e.Property(x => x.Id).HasColumnName("id");
            e.Property(x => x.Status).HasColumnName("status").IsRequired();
            e.Property(x => x.CreatedAt).HasColumnName("created_at");
            e.Property(x => x.CompletedAt).HasColumnName("completed_at");
        });

        modelBuilder.Entity<ParseAttemptEntity>(e =>
        {
            e.ToTable("parse_attempt");
            e.HasKey(x => x.Id);
            e.Property(x => x.Id).HasColumnName("id");
            e.Property(x => x.RawArtifactId).HasColumnName("raw_artifact_id");
            e.Property(x => x.ParserName).HasColumnName("parser_name");
            e.Property(x => x.ParserVersion).HasColumnName("parser_version");
            e.Property(x => x.Status).HasColumnName("status");
            e.Property(x => x.RecordsFound).HasColumnName("records_found");
            e.Property(x => x.AttemptCount).HasColumnName("attempt_count");
            e.Property(x => x.LeaseUntil).HasColumnName("lease_until");
            e.Property(x => x.LeaseOwner).HasColumnName("lease_owner");
            e.Property(x => x.LeaseGeneration).HasColumnName("lease_generation");
            e.Property(x => x.StartedAt).HasColumnName("started_at");
            e.Property(x => x.CompletedAt).HasColumnName("completed_at");
            e.Property(x => x.ErrorSummary).HasColumnName("error_summary");
            e.HasOne<RawArtifactEntity>().WithMany().HasForeignKey(x => x.RawArtifactId);
        });

        modelBuilder.Entity<ImportFileEntity>(e =>
        {
            e.ToTable("import_file");
            e.HasKey(x => x.Id);
            e.Property(x => x.Id).HasColumnName("id");
            e.Property(x => x.ImportJobId).HasColumnName("import_job_id");
            e.Property(x => x.RawArtifactId).HasColumnName("raw_artifact_id");
            e.Property(x => x.ParseAttemptId).HasColumnName("parse_attempt_id");
            e.Property(x => x.OriginalFilename).HasColumnName("original_filename");
            e.Property(x => x.Source).HasColumnName("source");
            e.Property(x => x.FilenameHints).HasColumnName("filename_hints").HasColumnType("jsonb");
            e.Property(x => x.Status).HasColumnName("status");
            e.Property(x => x.Deduplicated).HasColumnName("deduplicated");
            e.Property(x => x.DuplicateOfImportFileId).HasColumnName("duplicate_of_import_file_id");
            e.Property(x => x.CreatedAt).HasColumnName("created_at");
            e.Property(x => x.CompletedAt).HasColumnName("completed_at");
            e.HasOne<ImportJobEntity>().WithMany().HasForeignKey(x => x.ImportJobId);
            e.HasOne<RawArtifactEntity>().WithMany().HasForeignKey(x => x.RawArtifactId);
            e.HasOne<ParseAttemptEntity>().WithMany().HasForeignKey(x => x.ParseAttemptId);
        });

        modelBuilder.Entity<ArtifactPublicationEntity>(e =>
        {
            e.ToTable("artifact_publication");
            e.HasKey(x => x.RawArtifactId);
            e.Property(x => x.RawArtifactId).HasColumnName("raw_artifact_id");
            e.Property(x => x.ActiveParseAttemptId).HasColumnName("active_parse_attempt_id");
            e.Property(x => x.PublishedAt).HasColumnName("published_at");
            e.HasOne<RawArtifactEntity>().WithMany().HasForeignKey(x => x.RawArtifactId);
            e.HasOne<ParseAttemptEntity>().WithMany().HasForeignKey(x => x.ActiveParseAttemptId);
        });

        modelBuilder.Entity<ValidationResultEntity>(e =>
        {
            e.ToTable("validation_result");
            e.HasKey(x => x.Id);
            e.Property(x => x.Id).HasColumnName("id");
            e.Property(x => x.ParseAttemptId).HasColumnName("parse_attempt_id");
            e.Property(x => x.Code).HasColumnName("code");
            e.Property(x => x.Status).HasColumnName("status");
            e.Property(x => x.SourceValue).HasColumnName("source_value").HasPrecision(19, 6);
            e.Property(x => x.CalculatedValue).HasColumnName("calculated_value").HasPrecision(19, 6);
            e.Property(x => x.Difference).HasColumnName("difference").HasPrecision(19, 6);
            e.Property(x => x.Tolerance).HasColumnName("tolerance").HasPrecision(19, 6);
            e.Property(x => x.RuleVersion).HasColumnName("rule_version");
            e.Property(x => x.SourceLocator).HasColumnName("source_locator");
            e.HasOne<ParseAttemptEntity>().WithMany().HasForeignKey(x => x.ParseAttemptId);
        });

        modelBuilder.Entity<ParsedMovementEntity>(e =>
        {
            e.ToTable("parsed_movement");
            e.HasKey(x => x.Id);
            e.Property(x => x.Id).HasColumnName("id");
            e.Property(x => x.ParseAttemptId).HasColumnName("parse_attempt_id");
            e.Property(x => x.SourceRecordIndex).HasColumnName("source_record_index");
            e.Property(x => x.Direction).HasColumnName("direction");
            e.Property(x => x.ExternalProductId).HasColumnName("external_product_id");
            e.Property(x => x.ProductName).HasColumnName("product_name");
            e.Property(x => x.ExternalSaleId).HasColumnName("external_sale_id");
            e.Property(x => x.OccurredAt).HasColumnName("occurred_at").HasColumnType("timestamp without time zone");
            e.Property(x => x.Quantity).HasColumnName("quantity").HasPrecision(19, 6);
            e.Property(x => x.UnitPrice).HasColumnName("unit_price").HasPrecision(19, 2);
            e.Property(x => x.DiscountPercentage).HasColumnName("discount_percentage").HasPrecision(19, 6);
            e.Property(x => x.Total).HasColumnName("total").HasPrecision(19, 2);
            e.Property(x => x.PreviousStock).HasColumnName("previous_stock").HasPrecision(19, 6);
            e.Property(x => x.ResultingStock).HasColumnName("resulting_stock").HasPrecision(19, 6);
            e.Property(x => x.Manufacturer).HasColumnName("manufacturer");
            e.Property(x => x.SourceLocator).HasColumnName("source_locator");
            e.HasOne<ParseAttemptEntity>().WithMany().HasForeignKey(x => x.ParseAttemptId);
        });

        modelBuilder.Entity<ProductEntity>(e =>
        {
            e.ToTable("product");
            e.HasKey(x => x.Id);
            e.Property(x => x.Id).HasColumnName("id");
            e.Property(x => x.ExternalSource).HasColumnName("external_source");
            e.Property(x => x.ExternalId).HasColumnName("external_id");
            e.Property(x => x.Name).HasColumnName("name");
            e.Property(x => x.Unit).HasColumnName("unit");
            e.Property(x => x.FirstSeenParseAttemptId).HasColumnName("first_seen_parse_attempt_id");
            e.Property(x => x.CreatedAt).HasColumnName("created_at");
            e.Property(x => x.UpdatedAt).HasColumnName("updated_at");
            e.HasOne<ParseAttemptEntity>().WithMany().HasForeignKey(x => x.FirstSeenParseAttemptId);
        });

        modelBuilder.Entity<SaleEntity>(e =>
        {
            e.ToTable("sale");
            e.HasKey(x => x.Id);
            e.Property(x => x.Id).HasColumnName("id");
            e.Property(x => x.ExternalSource).HasColumnName("external_source");
            e.Property(x => x.ExternalSaleId).HasColumnName("external_sale_id");
            e.Property(x => x.OccurredAt).HasColumnName("occurred_at").HasColumnType("timestamp without time zone");
            e.Property(x => x.FirstSeenParseAttemptId).HasColumnName("first_seen_parse_attempt_id");
            e.Property(x => x.CreatedAt).HasColumnName("created_at");
            e.HasOne<ParseAttemptEntity>().WithMany().HasForeignKey(x => x.FirstSeenParseAttemptId);
        });

        modelBuilder.Entity<SaleItemEntity>(e =>
        {
            e.ToTable("sale_item");
            e.HasKey(x => x.Id);
            e.Property(x => x.Id).HasColumnName("id");
            e.Property(x => x.SaleId).HasColumnName("sale_id");
            e.Property(x => x.ProductId).HasColumnName("product_id");
            e.Property(x => x.ParseAttemptId).HasColumnName("parse_attempt_id");
            e.Property(x => x.SourceRecordIndex).HasColumnName("source_record_index");
            e.Property(x => x.Quantity).HasColumnName("quantity").HasPrecision(19, 6);
            e.Property(x => x.UnitPrice).HasColumnName("unit_price").HasPrecision(19, 2);
            e.Property(x => x.DiscountPercentage).HasColumnName("discount_percentage").HasPrecision(19, 6);
            e.Property(x => x.Total).HasColumnName("total").HasPrecision(19, 2);
            e.Property(x => x.PreviousStock).HasColumnName("previous_stock").HasPrecision(19, 6);
            e.Property(x => x.ResultingStock).HasColumnName("resulting_stock").HasPrecision(19, 6);
            e.HasOne<SaleEntity>().WithMany().HasForeignKey(x => x.SaleId);
            e.HasOne<ProductEntity>().WithMany().HasForeignKey(x => x.ProductId);
            e.HasOne<ParseAttemptEntity>().WithMany().HasForeignKey(x => x.ParseAttemptId);
        });
    }
}
