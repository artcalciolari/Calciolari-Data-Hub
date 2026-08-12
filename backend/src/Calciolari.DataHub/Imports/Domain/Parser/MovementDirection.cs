namespace Calciolari.DataHub.Imports.Domain.Parser;

/// <summary>
/// Movement direction taken only from positively identified source content.
/// Unknown stays UNKNOWN — never defaulted to OUT for revenue.
/// </summary>
public enum MovementDirection
{
    Out,
    In,
    Return,
    Unknown
}

public static class MovementDirectionNames
{
    public static string WireName(this MovementDirection direction) => direction switch
    {
        MovementDirection.Out => "OUT",
        MovementDirection.In => "IN",
        MovementDirection.Return => "RETURN",
        MovementDirection.Unknown => "UNKNOWN",
        _ => throw new ArgumentOutOfRangeException(nameof(direction), direction, null)
    };
}
