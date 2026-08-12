namespace Calciolari.DataHub.Imports.Infrastructure.InterPdv.Qrp;

/// <summary>
/// Text run extracted from EMR_EXTTEXTOUTW (type 84), as in the PoC parseEmfTexts.
/// </summary>
public sealed record EmfTextRun(int PageIndex, int X, int Y, string Text);
