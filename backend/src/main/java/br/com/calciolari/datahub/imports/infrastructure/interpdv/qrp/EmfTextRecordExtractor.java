package br.com.calciolari.datahub.imports.infrastructure.interpdv.qrp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Walks EMF records inside a page and extracts {@code EMR_EXTTEXTOUTW} text.
 *
 * <p>Evidence from PoC {@code parseEmfTexts}:
 * <ul>
 *   <li>record header: type u32 LE @0, size u32 LE @4</li>
 *   <li>type {@code 84} = EMR_EXTTEXTOUTW; requires {@code size >= 76}</li>
 *   <li>x i32 @36, y i32 @40, chars u32 @44, off u32 @48 (relative to record start)</li>
 *   <li>text UTF-16LE, {@code chars} code units</li>
 *   <li>type {@code 14} = EMR_EOF stops the walk</li>
 * </ul>
 */
public final class EmfTextRecordExtractor {

	static final int EMR_EOF = 14;
	static final int EMR_EXTTEXTOUTW = 84;
	static final int MIN_EXTTEXTOUTW_SIZE = 76;
	static final int MAX_CHARS = 10_000;
	static final int MAX_RECORDS_PER_PAGE = 100_000;

	private final int maxRecordsPerPage;
	private final int maxChars;

	public EmfTextRecordExtractor() {
		this(MAX_RECORDS_PER_PAGE, MAX_CHARS);
	}

	public EmfTextRecordExtractor(int maxRecordsPerPage, int maxChars) {
		if (maxRecordsPerPage < 1) {
			throw new IllegalArgumentException("maxRecordsPerPage must be >= 1");
		}
		if (maxChars < 1) {
			throw new IllegalArgumentException("maxChars must be >= 1");
		}
		this.maxRecordsPerPage = maxRecordsPerPage;
		this.maxChars = maxChars;
	}

	public List<EmfTextRun> extract(byte[] bytes, EmfPage page, int pageIndex) {
		Objects.requireNonNull(bytes, "bytes");
		Objects.requireNonNull(page, "page");
		if (page.endExclusive() > bytes.length) {
			throw new IllegalArgumentException("page exceeds buffer");
		}

		ByteBuffer view = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		List<EmfTextRun> out = new ArrayList<>();
		int pos = page.start();
		int end = page.endExclusive();
		int guard = 0;

		while (pos + 8 <= end && guard++ < maxRecordsPerPage) {
			int type = view.getInt(pos);
			long sizeLong = Integer.toUnsignedLong(view.getInt(pos + 4));
			if (sizeLong < 8 || sizeLong > Integer.MAX_VALUE) {
				break;
			}
			int size = (int) sizeLong;
			if (pos > end - size) {
				break;
			}

			if (type == EMR_EXTTEXTOUTW && size >= MIN_EXTTEXTOUTW_SIZE) {
				int x = view.getInt(pos + 36);
				int y = view.getInt(pos + 40);
				long charsLong = Integer.toUnsignedLong(view.getInt(pos + 44));
				long offLong = Integer.toUnsignedLong(view.getInt(pos + 48));
				if (charsLong <= maxChars && offLong <= Integer.MAX_VALUE && charsLong <= Integer.MAX_VALUE / 2) {
					int chars = (int) charsLong;
					int off = (int) offLong;
					int from = pos + off;
					int to = from + chars * 2;
					if (from >= pos && to >= from && to <= pos + size) {
						String text = decodeUtf16Le(bytes, from, to);
						if (!text.isEmpty()) {
							out.add(new EmfTextRun(pageIndex, x, y, text));
						}
					}
				}
			}

			pos += size;
			if (type == EMR_EOF) {
				break;
			}
		}
		return List.copyOf(out);
	}

	private static String decodeUtf16Le(byte[] bytes, int from, int to) {
		String raw = new String(bytes, from, to - from, StandardCharsets.UTF_16LE);
		return raw.replace("\u0000", "").trim();
	}
}
