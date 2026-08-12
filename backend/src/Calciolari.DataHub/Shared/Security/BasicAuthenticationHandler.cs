using System.Security.Claims;
using System.Security.Cryptography;
using System.Text;
using System.Text.Encodings.Web;
using Microsoft.AspNetCore.Authentication;
using Microsoft.Extensions.Options;

namespace Calciolari.DataHub.Shared.Security;

public sealed class BasicAuthenticationHandler : AuthenticationHandler<AuthenticationSchemeOptions>
{
    public const string SchemeName = "Basic";

    private readonly DataHubOptions _options;

    public BasicAuthenticationHandler(
        IOptionsMonitor<AuthenticationSchemeOptions> options,
        ILoggerFactory logger,
        UrlEncoder encoder,
        DataHubOptions dataHubOptions)
        : base(options, logger, encoder)
    {
        _options = dataHubOptions;
    }

    protected override Task<AuthenticateResult> HandleAuthenticateAsync()
    {
        if (!_options.SecurityEnabled)
        {
            var anonymous = new ClaimsPrincipal(new ClaimsIdentity(
            [
                new Claim(ClaimTypes.Name, "anonymous"),
                new Claim(ClaimTypes.Role, "VIEWER"),
                new Claim(ClaimTypes.Role, "IMPORTER"),
                new Claim(ClaimTypes.Role, "ADMIN")
            ], SchemeName));
            return Task.FromResult(AuthenticateResult.Success(new AuthenticationTicket(anonymous, SchemeName)));
        }

        if (!Request.Headers.TryGetValue("Authorization", out var header) || header.Count == 0)
        {
            return Task.FromResult(AuthenticateResult.Fail("Missing Authorization header"));
        }

        var value = header.ToString();
        if (!value.StartsWith("Basic ", StringComparison.OrdinalIgnoreCase))
        {
            return Task.FromResult(AuthenticateResult.Fail("Invalid Authorization scheme"));
        }

        string decoded;
        try
        {
            decoded = Encoding.UTF8.GetString(Convert.FromBase64String(value["Basic ".Length..].Trim()));
        }
        catch (FormatException)
        {
            return Task.FromResult(AuthenticateResult.Fail("Invalid basic credentials"));
        }

        var split = decoded.IndexOf(':');
        if (split < 0)
        {
            return Task.FromResult(AuthenticateResult.Fail("Invalid basic credentials"));
        }

        var username = decoded[..split];
        var password = decoded[(split + 1)..];
        var user = ParseUsers(_options).FirstOrDefault(u =>
            string.Equals(u.Username, username, StringComparison.Ordinal));
        if (user is null || !FixedTimeEquals(user.Password, password))
        {
            return Task.FromResult(AuthenticateResult.Fail("Invalid username or password"));
        }

        var claims = new List<Claim> { new(ClaimTypes.Name, user.Username) };
        claims.AddRange(user.Roles.Select(role => new Claim(ClaimTypes.Role, role)));
        var identity = new ClaimsIdentity(claims, SchemeName);
        return Task.FromResult(AuthenticateResult.Success(new AuthenticationTicket(new ClaimsPrincipal(identity), SchemeName)));
    }

    protected override Task HandleChallengeAsync(AuthenticationProperties properties)
    {
        Response.StatusCode = StatusCodes.Status401Unauthorized;
        Response.Headers.WWWAuthenticate = "Basic realm=\"datahub\"";
        return Task.CompletedTask;
    }

    protected override Task HandleForbiddenAsync(AuthenticationProperties properties)
    {
        Response.StatusCode = StatusCodes.Status403Forbidden;
        return Task.CompletedTask;
    }

    internal static IReadOnlyList<BasicUser> ParseUsers(DataHubOptions options)
    {
        var users = new List<BasicUser>();
        foreach (var entry in options.UserEntries())
        {
            users.Add(ParseUser(entry));
        }

        return users;
    }

    internal static BasicUser ParseUser(string entry)
    {
        var parts = entry.Split(':', 3);
        if (parts.Length != 3)
        {
            throw new ArgumentException(
                "Invalid datahub.security.users entry (expected user:pass:ROLE1|ROLE2): " + entry);
        }

        var username = parts[0].Trim();
        var password = parts[1].Trim();
        var roles = parts[2].Split('|', StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries);
        if (username.Length == 0 || password.Length == 0 || roles.Length == 0)
        {
            throw new ArgumentException("Incomplete datahub.security.users entry: " + entry);
        }

        return new BasicUser(username, password, roles);
    }

    internal static bool FixedTimeEquals(string left, string right)
    {
        var leftBytes = Encoding.UTF8.GetBytes(left);
        var rightBytes = Encoding.UTF8.GetBytes(right);
        if (leftBytes.Length != rightBytes.Length)
        {
            CryptographicOperations.FixedTimeEquals(leftBytes, leftBytes);
            return false;
        }

        return CryptographicOperations.FixedTimeEquals(leftBytes, rightBytes);
    }

    internal sealed record BasicUser(string Username, string Password, IReadOnlyList<string> Roles);
}
