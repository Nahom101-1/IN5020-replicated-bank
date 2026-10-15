package com.ass2.bank.cli;

import com.ass2.bank.core.Account;
import com.ass2.bank.core.BankReplicaImpl;
import com.ass2.bank.sync.SyncedBalance;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandProcessorTest {

    private static final Map<String, Double> RATES = Map.of("USD", 1.0, "NOK", 0.1, "EUR", 1.12);

    private final Account account = new Account("bank1", RATES);
    private final List<String> syncedOutput = new ArrayList<>();
    private final SyncedBalance syncedBalance = SyncedBalance.create("ordered", account, syncedOutput::add);
    private final BankReplicaImpl replica = new BankReplicaImpl(account);
    private final CommandProcessor processor = new CommandProcessor(account, syncedBalance, replica);

    CommandProcessorTest() {
        account.useSyncedBalance(syncedBalance);
    }

    /** Sends everything outstanding through the "total order", like the delivery server would. */
    private void deliverAll() {
        account.deliver(account.outstanding());
    }

    @Test
    void historyIsNumberedLikeTheExampleInTheAssignment() {
        // order_counter = 5 with two entries left after cleanHistory, as in the PDF example.
        processor.process("deposit NOK 1");
        processor.process("deposit NOK 2");
        processor.process("deposit NOK 3");
        deliverAll();
        processor.process("cleanHistory");
        processor.process("deposit NOK 100");
        processor.process("addInterest NOK 10");
        deliverAll();
        processor.process("deposit NOK -50");
        processor.process("addInterest NOK 5");

        String[] lines = processor.process("getHistory").split("\n");

        assertEquals("executed_list", lines[0]);
        assertTrue(lines[1].startsWith("3. ") && lines[1].endsWith(" deposit NOK 100"));
        assertTrue(lines[2].startsWith("4. ") && lines[2].endsWith(" addInterest NOK 10"));
        assertEquals("outstanding_collection", lines[3]);
        assertEquals("deposit NOK -50", lines[4]);
        assertEquals("addInterest NOK 5", lines[5]);
        assertEquals(6, lines.length);
    }

    @Test
    void historyDoesNotShowSyncedBalanceMarkers() {
        processor.process("getSyncedBalance NOK");

        assertEquals("executed_list\noutstanding_collection", processor.process("getHistory"));
    }

    @Test
    void checkTxStatusUsesOwnNameAndIgnoresMarkers() {
        // Same start as Rep1.txt: the deposit must get id 0 even after a getSyncedBalance.
        processor.process("getSyncedBalance USD");
        processor.process("deposit USD 90");

        assertEquals("Transaction bank1 0: outstanding, not applied yet", processor.process("checkTxStatus 0"));
        deliverAll();
        assertEquals("Transaction bank1 0: applied", processor.process("checkTxStatus 0"));
        assertEquals("Transaction bank1 9: not found", processor.process("checkTxStatus 9"));
    }

    @Test
    void quickBalanceIsNotSynced() {
        processor.process("deposit NOK 100");

        assertEquals("getQuickBalance NOK: 0.00", processor.process("getQuickBalance NOK"));
        deliverAll();
        assertEquals("getQuickBalance NOK: 100.00", processor.process("getQuickBalance NOK"));
    }

    @Test
    void balanceUsesUsdWhenNoCurrencyIsGiven() {
        processor.process("deposit NOK 100");
        deliverAll();

        assertEquals("getQuickBalance USD: 10.00", processor.process("getQuickBalance"));
        processor.process("getSyncedBalance");
        deliverAll();
        assertTrue(syncedOutput.get(0).startsWith("getSyncedBalance USD: 10.00"));
    }

    @Test
    void addInterestWithoutCurrencyAppliesToAll() {
        processor.process("deposit NOK 100");
        processor.process("deposit EUR 10");
        processor.process("addInterest 10");
        deliverAll();

        assertEquals(110.0, account.amountIn("NOK"), 1e-9);
        assertEquals(11.0, account.amountIn("EUR"), 1e-9);
    }

    @Test
    void rejectsUnknownCurrencyAndBadNumbers() {
        assertEquals("Unknown currency XYZ", processor.process("deposit XYZ 10"));
        assertEquals("Unknown currency XYZ", processor.process("getQuickBalance XYZ"));
        assertEquals("Invalid deposit amount.", processor.process("deposit NOK abc"));
        assertTrue(account.outstanding().isEmpty());
    }

    @Test
    void memberInfoShowsTheGroup() {
        replica.onMembershipChange(List.of("bank1", "bank2"));

        assertEquals("Members: bank1, bank2", processor.process("memberInfo"));
    }
}
