package com.sky.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sky.constant.JwtClaimsConstant;
import com.sky.constant.MessageConstant;
import com.sky.constant.PasswordConstant;
import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.EmployeeDTO;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.dto.PasswordEditDTO;
import com.sky.entity.Employee;
import com.sky.exception.PasswordEditFailedException;
import com.sky.mapper.EmployeeMapper;
import com.sky.properties.JwtProperties;
import com.sky.result.Result;
import com.sky.service.EmployeeService;
import com.sky.utils.JwtUtil;
import com.sky.vo.EmployeeLoginVO;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 员工管理
 */
@RestController
@RequestMapping("/admin/employee")
@Slf4j
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private EmployeeMapper employeeMapper;
    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 登录
     *
     * @param employeeLoginDTO
     * @return
     */
    @PostMapping("/login")
    public Result<EmployeeLoginVO> login(@RequestBody EmployeeLoginDTO employeeLoginDTO) {
        log.info("员工登录：{}", employeeLoginDTO);

        Employee employee = employeeService.login(employeeLoginDTO);

        //登录成功后，生成jwt令牌
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, employee.getId());
        String token = JwtUtil.createJWT(
                jwtProperties.getAdminSecretKey(),
                jwtProperties.getAdminTtl(),
                claims);

        EmployeeLoginVO employeeLoginVO = EmployeeLoginVO.builder()
                .id(employee.getId())
                .userName(employee.getUsername())
                .name(employee.getName())
                .token(token)
                .build();

        return Result.success(employeeLoginVO);
    }

    /**
     * 退出
     *
     * @return
     */
    @PostMapping("/logout")
    public Result<String> logout() {
        return Result.success();
    }

    /**
     * 新增员工
     * @param employeeDTO
     * @return
     */
    @PostMapping
    @ApiOperation("新增员工")
    public Result save(@RequestBody EmployeeDTO employeeDTO){

        log.info("新增员工，员工信息：{}",employeeDTO);

        Employee employee = new Employee();
        BeanUtils.copyProperties(employeeDTO,employee);
        //帐号默认状态为1，正常状态
        employee.setStatus(StatusConstant.ENABLE);
        //帐号初始密码为123456
        employee.setPassword(DigestUtils.md5DigestAsHex(PasswordConstant.DEFAULT_PASSWORD.getBytes()));

        employee.setCreateUser(BaseContext.getCurrentId());
        employee.setCreateTime(LocalDateTime.now());
        employee.setUpdateUser(BaseContext.getCurrentId());
        employee.setUpdateTime(LocalDateTime.now());

        employeeService.save(employee);

        return Result.success(employee);
    }

    /**
     * 修改密码
     * @param passwordEditDTO
     * @return
     */
    @PostMapping("/editPassword")
    @ApiOperation("修改密码")
    public Result editPassword(@RequestBody PasswordEditDTO passwordEditDTO){

        //获得当前登录用户id
        Long empId = passwordEditDTO.getEmpId();
        //根据id查询密码
        LambdaQueryWrapper<Employee> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Employee::getId,empId);
        Employee employee = employeeMapper.selectById(queryWrapper);
        //校对密码
        if(!DigestUtils.md5DigestAsHex(passwordEditDTO.getOldPassword().getBytes()).equals(employee.getPassword())){
            throw new PasswordEditFailedException(MessageConstant.PASSWORD_EDIT_FAILED);
        }
        //修改密码
        //update password from employee where id = ?
        LambdaUpdateWrapper<Employee> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(Employee::getPassword,passwordEditDTO.getNewPassword())
                        .eq(Employee::getId,empId);
        employeeService.update(employee,updateWrapper);

        return Result.success();
    }

    /**
     * 员工分页查询
     * @param employeePageQueryDTO
     * @return
     */
    @GetMapping("/page")
    @ApiOperation("员工分页查询")
    public Result<Page> page(EmployeePageQueryDTO employeePageQueryDTO){

        //设置分页参数
        Page pageInfo = new Page<>(employeePageQueryDTO.getPage(),employeePageQueryDTO.getPageSize());
        //构造条件构造器
        LambdaQueryWrapper<Employee> queryWrapper = new LambdaQueryWrapper<>();
        //添加过滤条件
        queryWrapper.like(StringUtils.isNotEmpty(employeePageQueryDTO.getName()),Employee::getName,employeePageQueryDTO.getName());
        //添加排序条件
        queryWrapper.orderByDesc(Employee::getCreateTime);
        //执行查询
        employeeService.page(pageInfo,queryWrapper);

        return Result.success(pageInfo);
    }

}
