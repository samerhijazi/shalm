package io.shalm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountServiceTest {

    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService();
        service.init();
    }

    @Test
    void seedsFourAccounts() {
        assertEquals(4, service.getAllAccounts().size());
    }

    @Test
    void getAccount_returnsSeededAccount() {
        Account acc = service.getAccount("ACC-B1-001");
        assertNotNull(acc);
        assertEquals("ClientA", acc.owner);
        assertEquals("Bank1", acc.bank);
        assertEquals(1000, acc.balance);
    }

    @Test
    void getAccount_unknownReturnsNull() {
        assertNull(service.getAccount("NOPE"));
    }

    @Test
    void createAccount_duplicateIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createAccount("ACC-B1-001", "X", "Bank1", 0));
    }

    @Test
    void deleteAccount_unknownReturnsFalse() {
        assertFalse(service.deleteAccount("NOPE"));
    }

    @Test
    void transfer_movesBalanceBetweenAccounts() {
        assertTrue(service.transfer("ACC-B1-001", "ACC-B2-001", 300));
        assertEquals(700, service.getAccount("ACC-B1-001").balance);
        assertEquals(1300, service.getAccount("ACC-B2-001").balance);
    }

    @Test
    void transfer_insufficientFundsFails() {
        assertFalse(service.transfer("ACC-B1-002", "ACC-B1-001", 999999));
        assertEquals(500, service.getAccount("ACC-B1-002").balance);
    }

    @Test
    void transfer_unknownAccountFails() {
        assertFalse(service.transfer("NOPE", "ACC-B1-001", 10));
    }
}
