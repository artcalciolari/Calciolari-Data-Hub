package br.com.calciolari.datahub.imports.infrastructure.interpdv.qrp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Locates embedded EMF pages inside a QRP blob.
 *
 * <p>Evidence from PoC {@code isEmfAt} / {@code findEmfPages}:
 * <ul>
 *   <li>EMR_HEADER type {@code 1} at offset {@code i}</li>
 *   <li>ASCII {@code " EMF"} (0x20 0x45 0x4D 0x46) at {@code i+40}</li>
 *   <li>page byte length {@code nBytes} LE u32 at {@code i+48}</li>
 *   <li>require {@code nBytes > 80} and {@code i + nBytes <= file length}</li>
 * </ul>
 */
public final class QrpContainerReader {

	static final int EMR_HEADER = 1;
	static final int EMF_SIGNATURE_OFFSET = 40;
	static final int EMF_NBYTES_OFFSET = 48;
	static final int MIN_HEADER_WINDOW = 56;
	static final int MIN_PAGE_BYTES = 80;

	/** ASCII {@code " EMF"} */
	private static final byte[] EMF_SIGNATURE = {0x20, 0x45, 0x4D, 0x46};

	private final int maxPages;

	public QrpContainerReader() {
		this(512);
	}

	public QrpContainerReader(int maxPages) {
		if (maxPages < 1) {
			throw new IllegalArgumentException("maxPages must be >= 1");
		}
		this.maxPages = maxPages;
	}

	public List<EmfPage> findEmfPages(byte[] bytes) {
		Objects.requireNonNull(bytes, "bytes");
		ByteBuffer view = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		List<EmfPage> pages = new ArrayList<>();
		for (int i = 0; i + MIN_HEADER_WINDOW < bytes.length; i++) {
			if (!isEmfAt(bytes, view, i)) {
				continue;
			}
			long nBytesLong = Integer.toUnsignedLong(view.getInt(i + EMF_NBYTES_OFFSET));
			if (nBytesLong > Integer.MAX_VALUE) {
				continue;
			}
			int nBytes = (int) nBytesLong;
			if (nBytes > MIN_PAGE_BYTES && i <= bytes.length - nBytes) {
				pages.add(new EmfPage(i, nBytes));
				if (pages.size() >= maxPages) {
					break;
				}
				i += nBytes - 1;
			}
		}
		return List.copyOf(pages);
	}

	static boolean isEmfAt(byte[] bytes, ByteBuffer view, int i) {
		if (i < 0 || i + MIN_HEADER_WINDOW > bytes.length) {
			return false;
		}
		if (view.getInt(i) != EMR_HEADER) {
			return false;
		}
		return bytes[i + EMF_SIGNATURE_OFFSET] == EMF_SIGNATURE[0]
				&& bytes[i + EMF_SIGNATURE_OFFSET + 1] == EMF_SIGNATURE[1]
				&& bytes[i + EMF_SIGNATURE_OFFSET + 2] == EMF_SIGNATURE[2]
				&& bytes[i + EMF_SIGNATURE_OFFSET + 3] == EMF_SIGNATURE[3];
	}
}
