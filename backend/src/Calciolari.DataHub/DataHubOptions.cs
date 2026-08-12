namespace Calciolari.DataHub;

public sealed class DataHubOptions
{
    public const string SectionName = "DataHub";

    public string ConnectionString { get; set; } = "Host=127.0.0.1;Port=5432;Database=datahub;Username=datahub;Password=change-me";

    public string RawStorageRoot { get; set; } = "./data/raw-storage";

    public int MaxFiles { get; set; } = 20;

    public long MaxFileBytes { get; set; } = 32L * 1024 * 1024;

    public long ParserMaxBytes { get; set; } = 32L * 1024 * 1024;

    public bool SecurityEnabled { get; set; }

    public bool SecurityRequireEnabled { get; set; }

    public string SecurityUsers { get; set; } = string.Empty;

    public string CorsAllowedOrigins { get; set; } = string.Empty;

    public IReadOnlyList<string> UserEntries()
    {
        if (string.IsNullOrWhiteSpace(SecurityUsers))
        {
            return [];
        }

        return SecurityUsers.Split(',', StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries);
    }

    public IReadOnlyList<string> OriginList()
    {
        if (string.IsNullOrWhiteSpace(CorsAllowedOrigins))
        {
            return [];
        }

        return CorsAllowedOrigins.Split(',', StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries);
    }

    public void ValidateOrThrow(string environmentName)
    {
        if (SecurityRequireEnabled && !SecurityEnabled)
        {
            throw new InvalidOperationException(
                "Profile requires DataHub:SecurityEnabled=true (environment="
                + environmentName + "). Do not expose the API without authentication.");
        }

        if (SecurityEnabled && UserEntries().Count == 0)
        {
            throw new InvalidOperationException(
                "DataHub:SecurityEnabled=true but DataHub:SecurityUsers is empty. Configure at least one user before starting.");
        }
    }
}
