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
    private static final String LOGIN_FAIL_IP_PREFIX = "login_fail_ip:";
    private static final String LOOKUP_RATE_PREFIX = "pwd_lookup_rate:"; // 忘记密码查询限流（IP）
    private static final long CODE_TTL_SECONDS = 300;
    private static final long RATE_LIMIT_SECONDS = 60;
    private static final int MAX_LOGIN_FAILS = 5;          // 同一IP+用户名最大失败次数
    private static final int MAX_IP_LOGIN_FAILS = 20;      // 同一IP最大失败次数(防跨用户名分布式爆破)

    /** 【安全 2026-08-08】忘记密码查询 IP 限流（60s，防匿名批量枚举用户名）；true=已限流 */
    public boolean lookupRateLimited(String ip) {
        String rateKey = LOOKUP_RATE_PREFIX + ip;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(rateKey))) return true;
        redisTemplate.opsForValue().set(rateKey, 1, RATE_LIMIT_SECONDS, TimeUnit.SECONDS);
        return false;
    }
    private static final long LOGIN_LOCK_SECONDS = 900;    // 锁定时间 15 分钟
    private static final int MAX_RESET_ERRORS = 5;         // 验证码最大错误次数
    private static final String RESET_IP_PREFIX = "pwd_reset_ip:";
    private static final int MAX_RESET_IP = 10;            // 同一IP窗口内最大发码次数(防短信轰炸/枚举驱动)
    private static final long RESET_IP_LOCK_SECONDS = 3600;

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

        // 【安全 2026-08-10】IP 级限流：防跨用户名分布式爆破(同一 IP 换不同账号试密码绕过用户名级计数)
        String ipFailKey = LOGIN_FAIL_IP_PREFIX + clientIp;
        Object ipFailObj = redisTemplate.opsForValue().get(ipFailKey);
        int ipFailCount = ipFailObj != null ? Integer.parseInt(ipFailObj.toString()) : 0;
        if (ipFailCount >= MAX_IP_LOGIN_FAILS) {
            throw new BusinessException(401, "登录尝试过于频繁，请稍后再试");
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
            ipFailCount++;
            redisTemplate.opsForValue().set(ipFailKey, ipFailCount, LOGIN_LOCK_SECONDS, TimeUnit.SECONDS);
            throw new BusinessException(401, "账号或密码错误");
        }

        // 登录成功，清除失败计数
        redisTemplate.delete(failKey);
        redisTemplate.delete(ipFailKey);

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(),
                request.getRememberMe() != null && request.getRememberMe());

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setRealName(user.getRealName());
        return response;
    }

    /** 【安全 2026-08-10 M2】注销：按 jti 拉黑当前 token, 立即失效(即使 token 被窃也无法复用) */
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            jwtTokenProvider.blacklistToken(token);
        }
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
        // 【修复 登录响应 dataScope 展示错误】此前取 roles.get(0)（数据库返回顺序的第一个角色），
        // 与多角色实际生效的"并集/用户级覆盖"后有效范围不一致，仅影响前端展示文案，不影响后端
        // 鉴权（鉴权已经过 UserDetailsServiceImpl 正确计算）。这里改为直接读取当前登录态已计算
        // 好的有效 dataScope，与实际生效范围保持一致。
        String effectiveDataScope = SecurityUtils.getCurrentDataScope();
        response.setDataScope(effectiveDataScope != null ? effectiveDataScope : "self");
        response.setPermissions(permissions);
        response.setMenus(menuTree);
        return response;
    }

    /**
     * 【修复 FEAT-20260804-DYNAMIC-MENU】构建下发给前端渲染侧边栏的菜单树。
     * selectMenusByUserId 返回的是该用户角色在 sys_role_menu 里勾选的全部菜单行，
     * 其中包含 menu_type=3 的按钮权限点（只用于 selectPermissionsByUserId 生成权限字符串，
     * 不应作为可点击的导航节点）以及 visible=0 的隐藏路由（如客户详情页）。
     * 这里过滤掉这两类，只把"目录(1)/菜单(2)"且 visible=1 的节点组成树返回给前端。
     */
    private List<SysMenu> buildMenuTree(List<SysMenu> menus, Long parentId) {
        return menus.stream()
                .filter(m -> Objects.equals(m.getParentId(), parentId))
                .filter(m -> m.getMenuType() != null && m.getMenuType() != 3)
                .filter(m -> m.getVisible() == null || m.getVisible() == 1)
                .peek(m -> m.setChildren(buildMenuTree(menus, m.getId())))
                .sorted(Comparator.comparingInt(m -> m.getSortOrder() != null ? m.getSortOrder() : 0))
                .collect(Collectors.toList());
    }

    // ── 忘记密码 ──

    public ForgotPasswordLookupResponse lookupUser(String username) {
        ForgotPasswordLookupResponse resp = new ForgotPasswordLookupResponse();
        SysUser user;
        try {
            user = findActiveUser(username);
        } catch (BusinessException e) {
            // 【安全 2026-08-08 匿名枚举防护】账号不存在也返回中性响应，不暴露账号是否存在
            return resp;
        }
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

    public void sendResetCode(ForgotPasswordSendCodeRequest request, String clientIp) {
        // 【对齐 2026-08-08】DTO 声明支持 email 但后端从未实现邮件发送；明确拒绝而非静默降级
        if ("email".equals(request.getMethod())) {
            throw new BusinessException(400, "邮箱验证暂未开通，请使用手机验证");
        }
        // 【安全 2026-08-10 M4】限流前置(在查用户之前) + IP 级计数每次请求:
        //   防枚举驱动短信轰炸(原来先查用户暴露存在性, 且仅按用户名限流, 换用户名可绕)
        String rateKey = RESET_RATE_PREFIX + request.getUsername() + ":phone";
        if (Boolean.TRUE.equals(redisTemplate.hasKey(rateKey))) {
            throw new BusinessException(429, "操作太频繁，请稍后再试");
        }
        String ipRateKey = RESET_IP_PREFIX + clientIp;
        Object ipRateObj = redisTemplate.opsForValue().get(ipRateKey);
        int ipSendCount = ipRateObj != null ? Integer.parseInt(ipRateObj.toString()) : 0;
        if (ipSendCount >= MAX_RESET_IP) {
            throw new BusinessException(429, "操作太频繁，请稍后再试");
        }
        // 每次请求都计 IP 次数(窗口滑动 1h), 无论用户是否存在——封死枚举/轰炸
        redisTemplate.opsForValue().increment(ipRateKey);
        redisTemplate.expire(ipRateKey, RESET_IP_LOCK_SECONDS, TimeUnit.SECONDS);

        SysUser user = findActiveUser(request.getUsername());

        if (user.getPhone() == null || user.getPhone().isBlank()) {
            throw new BusinessException(400, "该账号未绑定手机号");
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
        // 【修复 2026-08-10 P2-13】忘记密码重置后旧 token 立即失效
        jwtTokenProvider.markPasswordChanged(user.getId());
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
