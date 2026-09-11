package com.ruoyi.opc.hr.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.hr.domain.OpcHrApplication;
import com.ruoyi.opc.hr.domain.OpcHrOffer;
import com.ruoyi.opc.hr.dto.OpcHrOfferDto;
import com.ruoyi.opc.hr.mapper.OpcHrApplicationMapper;
import com.ruoyi.opc.hr.mapper.OpcHrOfferMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OpcHrOfferService 单测 (4 cases) — 唯一约束 + 响应 + 状态校验")
class OpcHrOfferServiceImplTest {

    private static final Long COMPANY_ID = 1L;
    private static final Long APPLICATION_ID = 300L;
    private static final Long OFFER_ID = 600L;

    @Mock
    private OpcHrOfferMapper offerMapper;

    @Mock
    private OpcHrApplicationMapper applicationMapper;

    @InjectMocks
    private OpcHrOfferServiceImpl offerService;

    private OpcHrOfferDto sampleDto;

    @BeforeEach
    void setUp() {
        sampleDto = OpcHrOfferDto.builder()
                .companyId(COMPANY_ID)
                .applicationId(APPLICATION_ID)
                .salary(new BigDecimal("25000.00"))
                .startDate(LocalDate.of(2026, 10, 1))
                .expireAt(LocalDateTime.of(2026, 9, 25, 18, 0))
                .build();

        OpcHrApplication app = OpcHrApplication.builder()
                .id(APPLICATION_ID).companyId(COMPANY_ID).jobId(100L).candidateId(200L).build();
        when(applicationMapper.selectById(APPLICATION_ID, COMPANY_ID)).thenReturn(app);
    }

    /** Test 5 */
    @Test
    @DisplayName("create - 完整 dto 成功创建,默认 status=PENDING + sentAt=NOW")
    void create_success_statusPending() {
        when(offerMapper.selectByApplicationId(APPLICATION_ID)).thenReturn(null);
        when(offerMapper.insert(any(OpcHrOffer.class))).thenReturn(1);

        Long id = offerService.create(sampleDto);

        assertThat(id).isNotNull().isPositive();
        ArgumentCaptor<OpcHrOffer> captor = ArgumentCaptor.forClass(OpcHrOffer.class);
        verify(offerMapper).insert(captor.capture());
        OpcHrOffer saved = captor.getValue();
        assertThat(saved.getCompanyId()).isEqualTo(COMPANY_ID);
        assertThat(saved.getApplicationId()).isEqualTo(APPLICATION_ID);
        assertThat(saved.getSalary()).isEqualByComparingTo(new BigDecimal("25000.00"));
        assertThat(saved.getStartDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(saved.getExpireAt()).isEqualTo(LocalDateTime.of(2026, 9, 25, 18, 0));
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getSentAt()).isNotNull();
        assertThat(saved.getId()).isEqualTo(id);
    }

    /** Test 6 */
    @Test
    @DisplayName("create - 已有 offer 时抛 ServiceException(uk_application)")
    void create_duplicateApplication_throws() {
        OpcHrOffer existing = OpcHrOffer.builder()
                .id(OFFER_ID).companyId(COMPANY_ID).applicationId(APPLICATION_ID)
                .status("PENDING").build();
        when(offerMapper.selectByApplicationId(APPLICATION_ID)).thenReturn(existing);

        assertThatThrownBy(() -> offerService.create(sampleDto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已有 Offer");
        verify(offerMapper, never()).insert(any(OpcHrOffer.class));
    }

    /** Test 7 */
    @Test
    @DisplayName("respond - PENDING -> ACCEPTED,setRespondedAt")
    void respond_accept_success() {
        OpcHrOffer existing = OpcHrOffer.builder()
                .id(OFFER_ID).companyId(COMPANY_ID).applicationId(APPLICATION_ID)
                .status("PENDING").build();
        when(offerMapper.selectById(OFFER_ID, COMPANY_ID)).thenReturn(existing);
        when(offerMapper.updateById(any(OpcHrOffer.class))).thenReturn(1);

        offerService.respond(OFFER_ID, COMPANY_ID, "ACCEPTED");

        ArgumentCaptor<OpcHrOffer> captor = ArgumentCaptor.forClass(OpcHrOffer.class);
        verify(offerMapper).updateById(captor.capture());
        OpcHrOffer updated = captor.getValue();
        assertThat(updated.getStatus()).isEqualTo("ACCEPTED");
        assertThat(updated.getRespondedAt()).isNotNull();
    }

    /** Test 8 */
    @Test
    @DisplayName("respond - 非法 status=FOOBAR 抛 ServiceException")
    void respond_invalidStatus_throws() {
        assertThatThrownBy(() -> offerService.respond(OFFER_ID, COMPANY_ID, "FOOBAR"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("FOOBAR");
        verify(offerMapper, never()).updateById(any(OpcHrOffer.class));
    }
}
