package app.renami.rename;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferActionTest {

    @Test
    void parsesAllChoicesCaseInsensitively() {
        assertEquals(TransferAction.RENAME, TransferAction.parse("rename"));
        assertEquals(TransferAction.MOVE, TransferAction.parse(" Move "));
        assertEquals(TransferAction.COPY, TransferAction.parse("COPY"));
        assertEquals(TransferAction.HARDLINK, TransferAction.parse("hardlink"));
        assertEquals(TransferAction.SYMLINK, TransferAction.parse("Symlink"));
    }

    @Test
    void unknownChoiceListsTheOptions() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> TransferAction.parse("delete"));
        assertTrue(e.getMessage().contains(TransferAction.CHOICES));
    }

    @Test
    void onlyMoveLikeActionsConsumeTheSource() {
        assertTrue(TransferAction.RENAME.consumesSource());
        assertTrue(TransferAction.MOVE.consumesSource());
        assertFalse(TransferAction.COPY.consumesSource());
        assertFalse(TransferAction.HARDLINK.consumesSource());
        assertFalse(TransferAction.SYMLINK.consumesSource());
    }

    @Test
    void verbsMatchJournalVocabulary() {
        assertEquals("renamed", TransferAction.RENAME.verb());
        assertEquals("hardlinked", TransferAction.HARDLINK.verb());
        assertEquals("symlinked", TransferAction.SYMLINK.verb());
    }
}
