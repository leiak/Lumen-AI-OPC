package com.ruoyi.opc.billing.service.impl;

import com.ruoyi.opc.billing.domain.OpcTransaction;
import com.ruoyi.opc.billing.domain.OpcWallet;
import com.ruoyi.opc.billing.mapper.OpcTransactionMapper;
import com.ruoyi.opc.billing.mapper.OpcWalletMapper;
import com.ruoyi.opc.common.exception.OpcException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * W2.1 — OpcWalletServiceImpl 单测。
 *
 * <p>覆盖：getOrCreate / recharge / consume / refund / grant / 幂等 / 余额不足 / 非法金额。
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class OpcWalletServiceImplTest {

    @Mock
    private OpcWalletMapper walletMapper;

    @Mock
    private OpcTransactionMapper txMapper;

    @InjectMocks
    private OpcWalletServiceImpl walletService;

    private static final Long COMPANY = 1001L;
    private static final Long USER = 2002L;
    private static final Long WALLET_ID = 7L;

    private OpcWallet wallet;

    @BeforeEach
    void setUp() {
        wallet = new OpcWallet();
        wallet.setId(WALLET_ID);
        wallet.setCompanyId(COMPANY);
        wallet.setUserId(USER);
        wallet.setBalance(new BigDecimal("100.0000"));
        wallet.setTotalRecharge(BigDecimal.ZERO);
        wallet.setTotalConsume(BigDecimal.ZERO);
        wallet.setStatus("ACTIVE");
        wallet.setCreateTime(new Date());
    }

    // ==================== getOrCreate ====================

    @Test
    @DisplayName("钱包不存在 → 自动创建并返回")
    void testGetOrCreate_New() {
        when(walletMapper.selectByCompanyUser(COMPANY, USER)).thenReturn(null);

        OpcWallet created = walletService.getOrCreate(COMPANY, USER);

        assertNotNull(created);
        assertEquals(COMPANY, created.getCompanyId());
        assertEquals(USER, created.getUserId());
        assertEquals(0, created.getBalance().compareTo(BigDecimal.ZERO));
        verify(walletMapper).insert(any(OpcWallet.class));
    }

    @Test
    @DisplayName("钱包已存在 → 不重复 insert")
    void testGetOrCreate_Existing() {
        when(walletMapper.selectByCompanyUser(COMPANY, USER)).thenReturn(wallet);

        OpcWallet w = walletService.getOrCreate(COMPANY, USER);

        assertEquals(WALLET_ID, w.getId());
        verify(walletMapper, never()).insert(any(OpcWallet.class));
    }

    // ==================== recharge ====================

    @Test
    @DisplayName("recharge 余额 + 写 RECHARGE 流水")
    void testRecharge() {
        when(walletMapper.selectByCompanyUser(COMPANY, USER)).thenReturn(wallet);
        when(walletMapper.recharge(eq(WALLET_ID), any())).thenReturn(1);

        OpcTransaction tx = walletService.recharge(COMPANY, USER,
                new BigDecimal("50.00"), "RECHARGE_ORDER", "ORD-001", "测试充值");

        assertEquals(OpcWalletServiceImpl.TX_RECHARGE, tx.getTxType());
        assertEquals(0, tx.getAmount().compareTo(new BigDecimal("50.00")));
        assertEquals(0, tx.getBalanceBefore().compareTo(new BigDecimal("100.0000")));
        assertEquals(0, tx.getBalanceAfter().compareTo(new BigDecimal("150.0000")));
        assertEquals("ORD-001", tx.getTxCode());
        verify(txMapper).insert(any(OpcTransaction.class));
    }

    @Test
    @DisplayName("recharge 幂等：同 txCode 二次调用 → 返回旧流水，不重复扣款")
    void testRecharge_Idempotent() {
        when(walletMapper.selectByCompanyUser(COMPANY, USER)).thenReturn(wallet);
        when(walletMapper.recharge(eq(WALLET_ID), any())).thenReturn(1);

        // 模拟唯一键冲突：第 1 次 insert 抛 Duplicate（retry 探测），第 2 次 insert 成功
        OpcTransaction existTx = new OpcTransaction();
        existTx.setId(99L);
        existTx.setTxCode("ORD-001");
        doThrow(new RuntimeException("Duplicate entry"))
                .doReturn(1)
                .when(txMapper).insert(any(OpcTransaction.class));
        when(txMapper.selectByTxCode("ORD-001")).thenReturn(existTx);

        OpcTransaction tx = walletService.recharge(COMPANY, USER,
                new BigDecimal("50.00"), "RECHARGE_ORDER", "ORD-001", "测试");

        assertEquals(99L, tx.getId());
        // recharge mapper 只应被调一次（首次）
        verify(walletMapper, times(1)).recharge(eq(WALLET_ID), any());
    }

    // ==================== consume ====================

    @Test
    @DisplayName("consume 余额充足 → 扣款 + 写 CONSUME 流水")
    void testConsume_Success() {
        when(walletMapper.selectByCompanyUser(COMPANY, USER)).thenReturn(wallet);
        when(walletMapper.deductBalance(eq(WALLET_ID), any())).thenReturn(1);

        OpcTransaction tx = walletService.consume(COMPANY, USER,
                new BigDecimal("30.00"), "AGENT_RUN", 123L, "AI 调用扣费");

        assertEquals(OpcWalletServiceImpl.TX_CONSUME, tx.getTxType());
        assertEquals(0, tx.getBalanceBefore().compareTo(new BigDecimal("100.0000")));
        assertEquals(0, tx.getBalanceAfter().compareTo(new BigDecimal("70.0000")));
        verify(walletMapper).deductBalance(eq(WALLET_ID), eq(new BigDecimal("30.00")));
    }

    @Test
    @DisplayName("consume 余额不足 → 抛 OpcException，不写流水")
    void testConsume_Insufficient() {
        when(walletMapper.selectByCompanyUser(COMPANY, USER)).thenReturn(wallet);

        OpcException ex = assertThrows(OpcException.class, () ->
                walletService.consume(COMPANY, USER,
                        new BigDecimal("200.00"), "AGENT_RUN", 1L, "超扣"));

        assertTrue(ex.getMessage().contains("余额不足"));
        verify(walletMapper, never()).deductBalance(anyLong(), any());
        verify(txMapper, never()).insert(any());
    }

    @Test
    @DisplayName("consume 金额<=0 → 抛 OpcException")
    void testConsume_InvalidAmount() {
        assertThrows(OpcException.class, () ->
                walletService.consume(COMPANY, USER,
                        BigDecimal.ZERO, "X", 1L, "0 金额"));
        assertThrows(OpcException.class, () ->
                walletService.consume(COMPANY, USER,
                        new BigDecimal("-1"), "X", 1L, "负金额"));
    }

    // ==================== refund / grant ====================

    @Test
    @DisplayName("refund 退款 → 余额+金额 + 写 REFUND 流水")
    void testRefund() {
        when(walletMapper.selectByCompanyUser(COMPANY, USER)).thenReturn(wallet);
        when(walletMapper.recharge(eq(WALLET_ID), any())).thenReturn(1);

        OpcTransaction tx = walletService.refund(COMPANY, USER,
                new BigDecimal("10.00"), "RECHARGE_ORDER", 1024L, "退款");

        assertEquals(OpcWalletServiceImpl.TX_REFUND, tx.getTxType());
        assertEquals(0, tx.getBalanceAfter().compareTo(new BigDecimal("110.0000")));
    }

    @Test
    @DisplayName("grant 邀请奖励 → 余额+金额 + 写 REWARD 流水")
    void testGrant_InviteReward() {
        when(walletMapper.selectByCompanyUser(COMPANY, USER)).thenReturn(wallet);
        when(walletMapper.recharge(eq(WALLET_ID), any())).thenReturn(1);

        OpcTransaction tx = walletService.grant(COMPANY, USER,
                new BigDecimal("50.00"), "INVITE_REWARD", 999L, "邀请好友奖励");

        assertEquals(OpcWalletServiceImpl.TX_REWARD, tx.getTxType());
        assertEquals(0, tx.getBalanceAfter().compareTo(new BigDecimal("150.0000")));
        assertEquals("INVITE_REWARD", tx.getBizType());
    }

    // ==================== listTransactions ====================

    @Test
    @DisplayName("listTransactions 按公司+类型查流水")
    void testListTransactions() {
        List<OpcTransaction> mockList = new ArrayList<>();
        when(txMapper.selectByCompany(COMPANY, "RECHARGE", 20)).thenReturn(mockList);

        List<OpcTransaction> result = walletService.listTransactions(COMPANY, "RECHARGE", 20);

        assertSame(mockList, result);
        verify(txMapper).selectByCompany(COMPANY, "RECHARGE", 20);
    }
}