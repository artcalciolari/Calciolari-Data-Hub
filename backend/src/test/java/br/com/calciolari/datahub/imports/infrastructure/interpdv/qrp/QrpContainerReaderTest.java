package br.com.calciolari.datahub.imports.infrastructure.interpdv.qrp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.calciolari.datahub.imports.support.FixturePackage;

class QrpContainerReaderTest {

	@Test
	void rejectsInvalidMaxPages() {
		assertThrows(IllegalArgumentException.class, () -> new QrpContainerReader(0));
	}

	@Test
	void findsPagesInFixture() {
		byte[] bytes = FixturePackage.requireBytes("fixture-a");
		List<EmfPage> pages = new QrpContainerReader().findEmfPages(bytes);
		assertFalse(pages.isEmpty());
		assertTrue(pages.get(0).length() > QrpContainerReader.MIN_PAGE_BYTES);
	}

	@Test
	void returnsEmptyWhenNoSignature() {
		assertTrue(new QrpContainerReader().findEmfPages(new byte[200]).isEmpty());
	}

	@Test
	void isEmfAtValidatesHeaderWindow() {
		byte[] bytes = new byte[128];
		ByteBuffer view = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		assertFalse(QrpContainerReader.isEmfAt(bytes, view, -1));
		assertFalse(QrpContainerReader.isEmfAt(bytes, view, 100));

		view.putInt(0, QrpContainerReader.EMR_HEADER);
		bytes[QrpContainerReader.EMF_SIGNATURE_OFFSET] = 0x20;
		bytes[QrpContainerReader.EMF_SIGNATURE_OFFSET + 1] = 0x45;
		bytes[QrpContainerReader.EMF_SIGNATURE_OFFSET + 2] = 0x4D;
		bytes[QrpContainerReader.EMF_SIGNATURE_OFFSET + 3] = 0x46;
		view.putInt(QrpContainerReader.EMF_NBYTES_OFFSET, 200);
		assertTrue(QrpContainerReader.isEmfAt(bytes, view, 0));
	}

	@Test
	void craftedMinimalPageIsDiscovered() {
		byte[] bytes = new byte[300];
		ByteBuffer view = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		view.putInt(0, QrpContainerReader.EMR_HEADER);
		bytes[40] = 0x20;
		bytes[41] = 0x45;
		bytes[42] = 0x4D;
		bytes[43] = 0x46;
		view.putInt(48, 120);
		List<EmfPage> pages = new QrpContainerReader(1).findEmfPages(bytes);
		assertEquals(1, pages.size());
		assertEquals(0, pages.get(0).start());
		assertEquals(120, pages.get(0).length());
	}

	@Test
	void requiresNonNullBytes() {
		assertThrows(NullPointerException.class, () -> new QrpContainerReader().findEmfPages(null));
	}
}
