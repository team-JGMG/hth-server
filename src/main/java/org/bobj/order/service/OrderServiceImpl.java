package org.bobj.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.bobj.order.domain.OrderVO;
import org.bobj.order.domain.OrderStatus;
import org.bobj.order.domain.OrderType;
import org.bobj.order.dto.request.OrderRequestDTO;
import org.bobj.order.dto.response.OrderResponseDTO;
import org.bobj.order.mapper.OrderMapper;
import org.bobj.orderbook.service.OrderBookService;
import org.bobj.orderbook.service.OrderBookWebSocketService;
import org.bobj.share.mapper.ShareMapper;
import org.bobj.share.service.ShareService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;


@Log4j2
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final ShareMapper shareMapper;
//    final private TradeMapper tradeMapper;
    private final ShareService shareService;

    //주문 체결 서비스
    private final OrderMatchingService orderMatchingService;
    private final OrderBookWebSocketService orderBookWebSocketService;

    @Override
    public OrderResponseDTO getOrderById(Long orderId) {
        OrderResponseDTO board = OrderResponseDTO.of(orderMapper.get(orderId));

        return Optional.ofNullable(board).orElseThrow(NoSuchElementException::new);
    }


    @Transactional
    @Override
    public OrderResponseDTO placeOrder(OrderRequestDTO orderRequestDTO) {

        OrderVO orderVO = orderRequestDTO.toVo();

        // 1. 펀딩 상태 확인
//        String fundingStatus = mapper.findFundingStatusByFundingId(orderBookVO.getFundingId());
//        if (!"ENDED".equals(fundingStatus)) {
//            throw new IllegalStateException("펀딩이 종료된 후에만 거래가 가능합니다.");
//        }

        // 2. 매도인일 경우 주식 보유 수량 확인
        if ("SELL".equalsIgnoreCase(String.valueOf(orderVO.getOrderType()))) {
            Integer userShareCount = shareMapper.findUserShareCount(orderVO.getUserId(), orderVO.getFundingId());

            // 보유 수량이 없는 경우
            if (userShareCount == null || userShareCount == 0) {
                throw new IllegalStateException("해당 종목을 보유하고 있지 않습니다.");
            }

            // 수량은 있으나 매도 수량보다 적은 경우
            if (userShareCount < orderVO.getOrderShareCount()) {
                throw new IllegalArgumentException("보유 주식 수량보다 많은 수량을 매도할 수 없습니다.");
            }
        }

        // 3. 매수인일 경우, 총 발행 주식 수보다 많은 수량을 주문하는 경우 체크
//        if ("BUY".equals(orderBookVO.getOrderType())) {
//            int totalIssuedShares = mapper.findTotalIssuedShares(orderBookVO.getFundingId());
//            int totalBuyOrderCount = mapper.findTotalBuyOrderCount(orderBookVO.getFundingId());
//            if (totalBuyOrderCount + orderBookVO.getOrderCount() > totalIssuedShares) {
//                throw new IllegalArgumentException("총 발행 주식 수를 초과하는 수량은 매수할 수 없습니다.");
//            }
//        }
//
//        // 4. 중복 주문 방지
//        boolean alreadyTraded = mapper.isOrderAlreadyTraded(orderBookVO);
//        if (alreadyTraded) {
//            throw new IllegalStateException("이미 체결된 주문입니다.");
//        }


        // 주문 저장
        orderMapper.create(orderVO);

        // 체결 시도
        orderMatchingService.processOrderMatching(orderVO);

        return getOrderById(orderVO.getOrderId());
    }

    @Override
    public List<OrderResponseDTO> getOrderHistoryByUserId(Long userId, String orderTypeStr) {
        OrderType orderType = null;

        if (orderTypeStr != null && !orderTypeStr.trim().isEmpty()) {
            try {
                orderType = OrderType.valueOf(orderTypeStr.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("유효하지 않은 주문 타입입니다. (BUY 또는 SELL만 가능)");
            }
        }

        List<OrderVO> orderBooks = orderMapper.getOrderHistoryByUserId(userId, orderType != null ? orderType.name() : null);
        return orderBooks.stream().map(OrderResponseDTO::of).toList();
    }

    @Transactional
    @Override
    public void cancelOrder(Long orderId) {
        OrderVO orderBook = orderMapper.getForUpdate(orderId);

        if (orderBook.getStatus() == OrderStatus.FULLY_FILLED) {
            throw new IllegalStateException("이미 전량 체결된 주문은 취소할 수 없습니다.");
        }

        if (orderBook.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("이미 취소된 주문입니다.");
        }

        orderMapper.cancelOrder(orderId);

        // 주문 취소 후 호가창 업데이트를 웹소켓으로 푸시
        orderBookWebSocketService.publishOrderBookUpdate(orderBook.getFundingId());
    }
}
