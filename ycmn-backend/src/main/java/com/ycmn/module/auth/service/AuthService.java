package com.ycmn.module.auth.service;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.ycmn.common.BusinessException;
import com.ycmn.config.SmsConfig;
import com.ycmn.module.auth.dto.*;
import com.ycmn.module.system.entity.SysDept;
import com.ycmn.module.system.entity.SysMenu;
import com.ycmn.module.system.entity.SysRole;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.mapper.*;
import com.ycmn.security.JwtTokenProvider;
import com.ycmn.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String RESET_CODE_PREFIX = "pwd_reset:";
    private static final String RESET_RATE_PREFIX = "pwd_reset_rate:";
    private static final String RESET_ERROR_PREFIX = "pwd_reset_error:";
    private static final String LOGIN_FAIL_PREFIX = "login_fail:";
    private static final long CODE_TTL_SECONDS = 300;
    private static final long RATE_LIMIT_SECONDS = 60;
    private static final int MAX_LOGIN_FAILS = 5;          // 同一IP+用户名最大失败次数
    private static final long LOGIN_LOCK_SECONDS = 900;    // 锁定时间 15 分钟
    private static final int MAX_RESET_ERRORS = 5;         // 验证码最大错误次数

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;
    private final SysDeptMapper deptMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;
    private final Client smsClient;
    private final SmsConfig smsConfig;

    public LoginResponse login(LoginRequest request, String clientIp) {
        String username = request.getUsername() != null ? request.getUsername().trim() : "";

        // 登录失败次数限流：同一 IP + 用户名
        String failKey = LOGIN_FAIL_PREFIX + clientIp + ":" + username;
        Object failCountObj = redisTemplate.opsForValue().get(failKey);
        int failCount = failCountObj != null ? Integer.parseInt(failCountObj.toString()) : 0;
        if (failCount >= MAX_LOGIN_FAILS) {
            throw new BusinessException(401, "登录失败次数过多，请15分钟后再试");
        }

        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username)
        );

        // 统一错误提示（不区分账号不存在/密码错误）
        if (user == null || user.getStatus() == 0
                || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            failCount++;
            redisTemplate.opsForValue().set(failKey, failCount, LOGIN_LOCK_SECONDS, TimeUnit.SECONDS);
            throw new BusinessException(401, "账号或密码错误");
        }

        // 登录成功，清除失败计数
        redisTemplate.delete(failKey);

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(),
                request.getRememberMe() != null && request.getRememberMe());

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setRealName(user.getRealName());
        return response;
    }

    public UserInfoResponse getUserInfo() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }

        SysUser user = userMapper.selectById(userId);
        List<SysRole> roles = roleMapper.selectRolesByUserId(userId);
        List<SysMenu> menus = menuMapper.selectMenusByUserId(userId);
        Set<String> permissions = menuMapper.selectPermissionsByUserId(userId);

        // 获取部门和办事处名称
        String deptName = "";
        String officeName = "";
        if (user.getDeptId() != null) {
            SysDept dept = deptMapper.selectById(user.getDeptId());
            if (dept != null) deptName = dept.getDeptName();
        }
        if (user.getOfficeId() != null) {
            SysDept office = deptMapper.selectById(user.getOfficeId());
            if (office != null) officeName = office.getDeptName();
        }

        // 构建菜单树
        List<SysMenu> menuTree = buildMenuTree(menus, 0L);

        UserInfoResponse response = new UserInfoResponse();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setRealName(user.getRealName());
        response.setAvatar(user.getAvatar());
        response.setDeptName(deptName);
        response.setOfficeName(officeName);
        response.setRoles(roles.stream().map(SysRole::getRoleCode).collect(Collectors.toList()));
        response.setRoleName(roles.isEmpty() ? "" : roles.get(0).getRoleName());
        response.setDataScope(roles.isEmpty() ? "self" : (roles.get(0).getDataScope() != null ? roles.get(0).getDataScope() : "self"));
        response.setPermissions(permissions);
        response.setMenus(menuTree);
        return response;
    }

    private List<SysMenu> buildMenuTree(List<SysMenu> menus, Long parentId) {
        return menus.stream()
                .filter(m -> Objects.equals(m.getParentId(), parentId))
                .peek(m -> m.setChildren(buildMenuTree(menus, m.getId())))
                .sorted(Comparator.comparingInt(m -> m.getSortOrder() != null ? m.getSortOrder() : 0))
                .collect(Collectors.toList());
    }

    // ── 忘记密码 ──

    public ForgotPasswordLookupResponse lookupUser(String username) {
        SysUser user = findActiveUser(username);
        ForgotPasswordLookupResponse resp = new ForgotPasswordLookupResponse();
        resp.setHasPhone(user.getPhone() != null && !user.getPhone().isBlank());
        resp.setHasEmail(user.getEmail() != null && !user.getEmail().isBlank());
        if (resp.isHasPhone()) {
            resp.setMaskedPhone(maskPhone(user.getPhone()));
        }
        if (resp.isHasEmail()) {
            resp.setMaskedEmail(maskEmail(user.getEmail()));
        }
        return resp;
    }

    public void sendResetCode(ForgotPasswordSendCodeRequest request) {
        SysUser user = findActiveUser(request.getUsername());

        if (user.getPhone() == null || user.getPhone().isBlank()) {
            throw new BusinessException(400, "该账号未绑定手机号");
        }

        // 限流检查
        String rateKey = RESET_RATE_PREFIX + request.getUsername() + ":phone";
        if (Boolean.TRUE.equals(redisTemplate.hasKey(rateKey))) {
            throw new BusinessException(429, "操作太频繁，请稍后再试");
        }

        // 生成6位验证码
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
        String codeKey = RESET_CODE_PREFIX + request.getUsername();
        redisTemplate.opsForValue().set(codeKey, code, CODE_TTL_SECONDS, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(rateKey, "1", RATE_LIMIT_SECONDS, TimeUnit.SECONDS);

        // 发送阿里云短信
        try {
            SendSmsRequest smsRequest = new SendSmsRequest()
                    .setPhoneNumbers(user.getPhone())
                    .setSignName(smsConfig.getSignName())
                    .setTemplateCode(smsConfig.getTemplateCode())
                    .setTemplateParam("{\"code\":\"" + code + "\"}");
            SendSmsResponse resp = smsClient.sendSms(smsRequest);
            if (!"OK".equals(resp.getBody().getCode())) {
                log.error("短信发送失败: {}, {}", resp.getBody().getCode(), resp.getBody().getMessage());
                throw new BusinessException(500, "短信发送失败: " + resp.getBody().getMessage());
            }
            log.info("【密码重置验证码】短信已发送至 {}，用户: {}", maskPhone(user.getPhone()), request.getUsername());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("短信发送异常", e);
            throw new BusinessException(500, "短信发送失败，请稍后重试");
        }
    }

    public void resetPassword(ForgotPasswordResetRequest request) {
        SysUser user = findActiveUser(request.getUsername());

        // 验证码错误次数限制
        String errorKey = RESET_ERROR_PREFIX + request.getUsername();
        Object errorCountObj = redisTemplate.opsForValue().get(errorKey);
        int errorCount = errorCountObj != null ? Integer.parseInt(errorCountObj.toString()) : 0;
        if (errorCount >= MAX_RESET_ERRORS) {
            throw new BusinessException(429, "验证码错误次数过多，请15分钟后再试");
        }

        String codeKey = RESET_CODE_PREFIX + request.getUsername();
        Object stored = redisTemplate.opsForValue().get(codeKey);
        if (stored == null) {
            throw new BusinessException(400, "验证码已过期，请重新获取");
        }
        if (!request.getCode().trim().equals(stored.toString().trim())) {
            errorCount++;
            redisTemplate.opsForValue().set(errorKey, errorCount, LOGIN_LOCK_SECONDS, TimeUnit.SECONDS);
            throw new BusinessException(400, "验证码错误");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
        redisTemplate.delete(codeKey);
        redisTemplate.delete(errorKey);
    }

    private SysUser findActiveUser(String username) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username)
                        .eq(SysUser::getDeleted, 0)
        );
        if (user == null) {
            throw new BusinessException(400, "用户不存在");
        }
        if (user.getStatus() == 0) {
            throw new BusinessException(400, "账号已被禁用");
        }
        return user;
    }

    private String maskPhone(String phone) {
        if (phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return email;
        return email.charAt(0) + "***" + email.substring(at);
    }
}
