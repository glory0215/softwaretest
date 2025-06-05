package com.meethere.service.impl;

import com.meethere.dao.OrderDao;

import com.meethere.dao.VenueDao;
import com.meethere.entity.Venue;
import com.meethere.entity.Order;
import com.meethere.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.security.InvalidParameterException;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderDao orderDao;

    @Autowired
    private VenueDao venueDao;

    @Override
    public Order findById(int OrderID) {
        return orderDao.getOne(OrderID);
    }

    @Override
    public List<Order> findDateOrder(int venueID, LocalDateTime startTime, LocalDateTime startTime2) {
        return orderDao.findByVenueIDAndStartTimeIsBetween(venueID,startTime,startTime2);
    }

    @Override
    public Page<Order> findUserOrder(String userID, Pageable pageable) {
        return orderDao.findAllByUserID(userID,pageable);
    }


    @Override
    public void updateOrder(int orderID, String venueName, LocalDateTime startTime, int hours,String userID)  {


        if (orderID <= 0) {
            throw new InvalidParameterException("订单ID无效，必须大于0。"); // 自定义异常
        }

        // venueName 校验
        if (venueName == null || venueName.trim().isEmpty()) {
            throw new InvalidParameterException("场馆名称不能为空。");
        }

        // hours 校验 (决策表用例 2: hours 无效或超出范围)
        if (hours <= 0) { // 假设小时数必须为正整数
            throw new InvalidParameterException("预订小时数必须是正整数。");
        }
        // 进一步校验 hours 是否在合理范围内 (例如：最大预订小时数)
        // if (hours > MAX_BOOKING_HOURS) {
        //     throw new InvalidParameterException("预订小时数超出最大限制。");
        // }


        // startTime 校验 (决策表用例 3: date 或 startTime 无效)
        if (startTime == null) {
            throw new InvalidParameterException("开始时间不能为空。");
        }
        // 校验 startTime 是否在未来 (与 date Valid 关联)
        if (startTime.isBefore(LocalDateTime.now())) {
            throw new InvalidParameterException("预订开始时间必须是未来的时间。");
        }
        // 进一步校验 startTime 是否在场馆的开放时间内（这需要Venue实体有开放时间信息）
        // 例如：if (!isWithinVenueOperatingHours(venue, startTime, hours)) {
        //     throw new InvalidParameterException("预订时间不在场馆开放时间内。");
        // }


        // userID 校验 (假设 userID 不能为空，虽然不在决策表中，但通常是必要参数)
        if (userID == null || userID.trim().isEmpty()) {
            throw new InvalidParameterException("用户ID不能为空。");
        }


        // --- 2. 业务逻辑校验和数据获取 ---

        // 获取场馆信息 (对应决策表中的 venueName Valid? 且 Exists in DB)
        Venue venue = venueDao.findByVenueName(venueName);
        if (venue == null) {
            throw new IllegalArgumentException("指定场馆不存在。"); // 自定义异常
        }

        // 获取订单信息 (对应决策表中的 orderID Valid? 且 Exists in DB)
        Order order = orderDao.findByOrderID(orderID);
        if (order == null) {
            // 决策表用例 5, 6: 订单ID不存在或无权限
            throw new IllegalArgumentException("订单ID不存在。"); // 自定义异常
        }

        // 校验用户权限 (对应决策表中的 "Belongs to User" 部分)
        if (!order.getUserID().equals(userID)) {
            // 如果修改订单需要用户权限，确保是订单的创建者或管理员
            throw new IllegalArgumentException("您无权修改此订单。"); // 自定义异常
        }



        order.setState(STATE_NO_AUDIT);
        order.setHours(hours);
        order.setVenueID(venue.getVenueID());
        order.setOrderTime(LocalDateTime.now());
        order.setStartTime(startTime);
        order.setUserID(userID);
        order.setTotal(hours* venue.getPrice());

        orderDao.save(order);
    }

    @Override
    public void submit(String venueName, LocalDateTime startTime, int hours, String userID) {

        Venue venue =venueDao.findByVenueName(venueName);

        Order order=new Order();
        order.setState(STATE_NO_AUDIT);
        order.setHours(hours);
        order.setVenueID(venue.getVenueID());
        order.setOrderTime(LocalDateTime.now());
        order.setStartTime(startTime);
        order.setUserID(userID);
        order.setTotal(hours* venue.getPrice());
        orderDao.save(order);
    }

    @Override
    public void delOrder(int orderID) {
        orderDao.deleteById(orderID);
    }

    @Override
    public void confirmOrder(int orderID) {
        Order order=orderDao.findByOrderID(orderID);
        if(order == null) {
            throw new RuntimeException("订单不存在");
        }
        orderDao.updateState(STATE_WAIT,order.getOrderID());
    }

    @Override
    public void finishOrder(int orderID) {
        Order order=orderDao.findByOrderID(orderID);
        if(order == null) {
            throw new RuntimeException("订单不存在");
        }
        orderDao.updateState(STATE_FINISH,order.getOrderID());
    }

    @Override
    public void rejectOrder(int orderID) {
        Order order=orderDao.findByOrderID(orderID);
        if(order == null) {
            throw new RuntimeException("订单不存在");
        }
        orderDao.updateState(STATE_REJECT,order.getOrderID());
    }

    @Override
    public Page<Order> findNoAuditOrder(Pageable pageable) {
        return orderDao.findAllByState(STATE_NO_AUDIT,pageable);
    }

    @Override
    public List<Order> findAuditOrder() {
        return orderDao.findAudit(STATE_WAIT,STATE_FINISH);
    }
}
