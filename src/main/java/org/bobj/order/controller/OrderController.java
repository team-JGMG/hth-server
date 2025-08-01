package org.bobj.order.controller;

import io.swagger.annotations.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.bobj.common.exception.ErrorResponse;
import org.bobj.common.response.ApiCommonResponse;
import org.bobj.order.dto.request.OrderRequestDTO;
import org.bobj.order.dto.response.OrderResponseDTO;
import org.bobj.order.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Log4j2
@Api(tags="거래 주문 API")
public class OrderController {

    private final OrderService service;

    @PostMapping("")
    @ApiOperation(value = "거래 주문 등록", notes = "새로운 거래 주문 정보를 등록합니다.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "거래 주문 성공", response = ApiCommonResponse.class),
            @ApiResponse(code = 400, message = "잘못된 요청 (펀딩 미종료, 주식 미보유, 수량 부족, 발행량 초과 등)\n\n" +
                    "**예시:**\n" +
                    "```json\n" +
                    "{\n" +
                    "  \"status\": 400,\n" +
                    "  \"code\": \"OB001\",\n" +
                    "  \"message\": \"펀딩이 종료된 후에만 거래가 가능합니다.\",\n" +
                    "  \"path\": \"/api/v1/orders\"\n" +
                    "}\n" +
                    "```\n\n" +
                    "```json\n" +
                    "{\n" +
                    "  \"status\": 400,\n" +
                    "  \"code\": \"OB002\",\n" +
                    "  \"message\": \"해당 종목을 보유하고 있지 않습니다.\",\n" +
                    "  \"path\": \"/api/v1/orders\"\n" +
                    "}\n" +
                    "```\n\n" +
                    "```json\n" +
                    "{\n" +
                    "  \"status\": 400,\n" +
                    "  \"code\": \"OB003\",\n" +
                    "  \"message\": \"보유 주식 수량보다 많은 수량을 매도할 수 없습니다.\",\n" +
                    "  \"path\": \"/api/v1/orders\"\n" +
                    "}\n" +
                    "```\n\n" +
                    "```json\n" +
                    "{\n" +
                    "  \"status\": 400,\n" +
                    "  \"code\": \"OB004\",\n" +
                    "  \"message\": \"총 발행 주식 수를 초과하는 수량은 매수할 수 없습니다.\",\n" +
                    "  \"path\": \"/api/v1/orders\"\n" +
                    "}\n" +
                    "```\n\n" +
                    "```json\n" +
                    "{\n" +
                    "  \"status\": 400,\n" +
                    "  \"code\": \"C001\",\n" +
                    "  \"message\": \"잘못된 입력 값입니다.\",\n" +
                    "  \"path\": \"/api/v1/orders\"\n" +
                    "}\n" +
                    "```",
                    response = ErrorResponse.class),
            @ApiResponse(code = 409, message = "충돌 (예: 이미 체결된 주문)\n\n" +
                    "**예시:**\n" +
                    "```json\n" +
                    "{\n" +
                    "  \"status\": 409,\n" +
                    "  \"code\": \"OB005\",\n" +
                    "  \"message\": \"이미 체결된 주문입니다.\",\n" +
                    "  \"path\": \"/api/v1/orders\"\n" +
                    "}\n" +
                    "```",
                    response = ErrorResponse.class),
            @ApiResponse(code = 500, message = "서버 내부 오류", response = ErrorResponse.class)
    })
    public  ResponseEntity<ApiCommonResponse<OrderResponseDTO>> placeOrder(
            @RequestBody @ApiParam(value = "거래 주문 DTO", required = true) OrderRequestDTO dto) {

        OrderResponseDTO created = service.placeOrder(dto);

        ApiCommonResponse<OrderResponseDTO> response = ApiCommonResponse.createSuccess(created);

        return ResponseEntity.ok(response);
    }

    @GetMapping("history/{userId}")
    @ApiOperation(value = "거래 주문 내역 조회", notes = "사용자의 거래 주문 내역을 조회합니다.")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "userId", value = "조회할 사용자 ID", required = false, dataType = "long", paramType = "path"),
            @ApiImplicitParam(name = "orderType", value = "주문 타입 (BUY, SELL)", required = false, dataType = "string", paramType = "query")
    })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "거래 주문 내역 조회 성공", response = OrderResponseDTO.class, responseContainer = "List"),
            @ApiResponse(code = 400, message = "잘못된 요청 (예: 유효하지 않은 파라미터)", response = ErrorResponse.class),
            @ApiResponse(code = 404, message = "조회된 주문 내역 없음", response = ErrorResponse.class), // 404는 보통 조회 결과가 없을 때 사용. 200 OK에 빈 리스트 반환이 일반적
            @ApiResponse(code = 500, message = "서버 내부 오류", response = ErrorResponse.class)
    })
    public ResponseEntity<ApiCommonResponse<List<OrderResponseDTO>>> getOrderHistory(
            @PathVariable Long userId,
            @RequestParam(value = "orderType", required = false) String orderType
    ) {
        List<OrderResponseDTO> orderHistory = service.getOrderHistoryByUserId(userId, orderType);

        return ResponseEntity.ok(ApiCommonResponse.createSuccess(orderHistory));
    }


    @PatchMapping("/{orderId}")
    @ApiOperation(value = "거래 주문 취소", notes = "주어진 주문 ID에 해당하는 거래 주문을 취소합니다.")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "orderId", value = "취소할 주문 ID", required = true, dataType = "long", paramType = "path")
    })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "주문 취소 성공", response = ApiCommonResponse.class),
            @ApiResponse(code = 400, message = "잘못된 요청 (예: 이미 취소된 주문)", response = ErrorResponse.class),
            @ApiResponse(code = 404, message = "해당 주문을 찾을 수 없음", response = ErrorResponse.class),
            @ApiResponse(code = 500, message = "서버 내부 오류", response = ErrorResponse.class)
    })
    public ResponseEntity<ApiCommonResponse<String>> cancelOrder(@PathVariable Long orderId) {
        service.cancelOrder(orderId);
        return ResponseEntity.ok(ApiCommonResponse.createSuccess("주문이 성공적으로 취소되었습니다."));
    }
}
