package org.bobj.property.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bobj.common.dto.CustomSlice;
import org.bobj.funding.mapper.FundingMapper;
import org.bobj.property.domain.PropertyStatus;
import org.bobj.property.domain.PropertyVO;
import org.bobj.property.dto.*;
import org.bobj.property.mapper.PropertyMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PropertyService {
    private final PropertyMapper propertyMapper;
    private final FundingMapper fundingMapper;
    private final RentalIncomeService rentalIncomeService;

    // 매물 승인 + 펀딩 등록 or 거절
    @Transactional
    public void updatePropertyStatus(Long propertyId, String status) {
        propertyMapper.update(propertyId,status);

        // 상태가 APPROVED일 때만 fundings 테이블에 insert
        if ("APPROVED".equalsIgnoreCase(status)) {
            fundingMapper.insertFunding(propertyId);
        }
    }

    // 매물 등록
    @Transactional
    public void registerProperty(PropertyCreateDTO dto) {
        PropertyVO vo = dto.toVO();
        
        // 매물 등록 (DB 트랜잭션 내에서 처리)
        propertyMapper.insert(vo);
        
        // insert 후 생성된 ID 확인
        log.info("매물 등록 완료 - ID: {}, 제목: {}", vo.getPropertyId(), vo.getTitle());
        
        // 트랜잭션 커밋 후 비동기로 월세 계산 처리
        if (vo.getPropertyId() != null && vo.getRawdCd() != null && !vo.getRawdCd().trim().isEmpty()) {
            // 트랜잭션 외부에서 API 호출 처리
            calculateAndUpdateRentalIncome(vo.getPropertyId(), vo.getRawdCd(), vo.getAddress());
        } else {
            if (vo.getPropertyId() != null) {
                if (vo.getRawdCd() == null || vo.getRawdCd().trim().isEmpty()) {
                    log.warn("법정동코드가 없어서 월세 자동 계산을 건너뛰었습니다 - 매물ID: {}", vo.getPropertyId());
                }
            } else {
                log.error("매물 등록 후 ID 생성 실패 - 월세 자동 계산 불가");
            }
        }
    }
    
    /**
     * 트랜잭션 외부에서 월세 계산 및 업데이트 처리
     * DB 커넥션 점유 시간을 최소화하기 위해 별도 메서드로 분리
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void calculateAndUpdateRentalIncome(Long propertyId, String rawdCd, String address) {
        try {
            log.info("매물 자동 월세 계산 시작 - 매물ID: {}, 법정동코드: {}, 주소: {}", propertyId, rawdCd, address);
            
            // API 호출로 월세 데이터 계산 (시간이 오래 걸릴 수 있음)
            BigDecimal calculatedRentalAmount = rentalIncomeService.getLatestMonthlyRentForProperty(rawdCd, address);
            
            if (calculatedRentalAmount != null) {
                // 새로운 트랜잭션에서 빠른 업데이트
                propertyMapper.updateRentalIncome(propertyId, calculatedRentalAmount);
                log.info("매물 월세 자동 설정 완료 - 매물ID: {}, 월세: {}원", propertyId, calculatedRentalAmount);
            } else {
                log.warn("매물 월세 자동 계산 실패 - 매물ID: {}, 매칭되는 월세 데이터 없음, 기본값 적용 안함", propertyId);
            }
            
        } catch (Exception e) {
            log.error("매물 월세 자동 계산 중 오류 발생 - 매물ID: {}, 오류: {}", propertyId, e.getMessage(), e);
            // 월세 계산 실패해도 매물 등록은 성공으로 처리 (이미 커밋됨)
        }
    }

    // 매물 리스트 조회(관리자 페이지)
    public CustomSlice<PropertyTotalDTO> getAllPropertiesByStatus(String category, int page, int size) {
        int offset = page * size;

        List<PropertyVO> vos = propertyMapper.findTotal(category, offset, size+1);
        boolean hasNext = vos.size() > size;
        if(hasNext) {
            vos.remove(size);
        }

        List<PropertyTotalDTO> dtoList = vos.stream()
                .map(PropertyTotalDTO::of)
                .collect(Collectors.toList());
        return new CustomSlice<>(dtoList,hasNext);
    }

    // 유저의 매물 리스트 조회(마이페이지)
    public CustomSlice<PropertyUserResponseDTO> getUserPropertiesByStatus(Long userId, String status, int page, int size) {
        int offset = page * size;
        String upperStatus = status.toUpperCase();

        List<PropertyUserResponseDTO> resultList =
                propertyMapper.findByUserId(userId, upperStatus, offset, size + 1);

        boolean hasNext = resultList.size() > size;
        if (hasNext) {
            resultList.remove(size);
        }

        // 펀딩 관련 계산 (APPROVED, SOLD일 때만 적용)
        if (upperStatus.equals("APPROVED") || upperStatus.equals("SOLD")) {
            for (PropertyUserResponseDTO dto : resultList) {
                if (dto.getTargetAmount() != null
                        && dto.getCurrentAmount() != null
                        && dto.getTargetAmount().compareTo(BigDecimal.ZERO) > 0) {

                    BigDecimal target = dto.getTargetAmount();
                    BigDecimal current = dto.getCurrentAmount();

                    int rate = current
                            .multiply(BigDecimal.valueOf(100))
                            .divide(target, 0, RoundingMode.DOWN)
                            .intValue();
                    dto.setAchievementRate(rate);

                    BigDecimal remainingAmount = target.subtract(current);
                    dto.setRemainingAmount(remainingAmount);

                    long remainingShares = remainingAmount
                            .divide(BigDecimal.valueOf(5000), 0, RoundingMode.DOWN)
                            .longValue();
                    dto.setRemainingShares(remainingShares);
                }
            }
        }

        return new CustomSlice<>(resultList, hasNext);
    }

    // 매물 상세 조회(관리자, 마이페이지(내가 올린 매물)
    public PropertyDetailDTO getPropertyById(Long propertyId) {
        PropertyVO vo = propertyMapper.findByPropertyId(propertyId);
        return PropertyDetailDTO.of(vo);
    }

    // 매각 완료 매물 리스트
    public List<PropertySoldResponseDTO> getSoldProperties() {
        return propertyMapper.findSold();
    }
}
