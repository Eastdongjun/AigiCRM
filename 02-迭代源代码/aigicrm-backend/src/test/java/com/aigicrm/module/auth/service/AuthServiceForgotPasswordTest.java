package com.aigicrm.module.auth.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.auth.PermEpochService;
import com.aigicrm.config.SmsConfig;
import com.aigicrm.module.auth.dto.ForgotPasswordLookupResponse;
import com.aigicrm.module.auth.dto.ForgotPasswordResetRequest;
import com.aigicrm.module.auth.dto.ForgotPasswordSendCodeRequest;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.*;
import com.aigicrm.module.system.service.RolePermScopeService;
import com.aigicrm.security.JwtTokenProvider;
import com.aigicrm.security.SecurityAuditService;
import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.dysmsapi20170525.models.SendSmsResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 【GL-14 2026-11-24】忘记密码链路的行为测试（此前这条匿名链路没有任何单元测试）。
 * 钉住的是对外可观察的行为：
 * 发码——验证码 6 位、5 分钟有效、60 秒内同一账号不能重发、同一 IP 每小时最多 10 次（不论账号是否存在）、
 *       邮箱方式明确拒绝、账号不存在/禁用/未绑手机一律“已受理”且不发码、短信失败报 500；
 * 重置——验证码正确才改密并让旧登录态失效、验证码用后即焚、错 5 次后锁定、没发过码不计错误次数、
 *       账号不存在与验证码错误是同一句回复（防枚举）、弱口令被拒且验证码仍有效；
 * 查询——联系方式打码、账号不存在时返回中性结果、按 IP 限流。
 * Redis 用内存映射模拟（同时记录每次写入的有效期）；短信客户端、审计、Mapper 都是 mock。
 * 验证码的随机源（必须是 SecureRandom，安全修复 9db17c8f）无法从行为上观察，由
 * scripts/check-secure-random-contract.sh 从源码上守。
 */
class AuthServiceForgotPasswordTest {

    private static final String CODE_KEY = "pwd_reset:alice";
    private static final String RATE_KEY = "pwd_reset_rate:alice:phone";
    private static final String ERROR_KEY = "pwd_reset_error:alice";
    /** 测试用的合成口令(满足口令策略: 8-30 位、同时含字母和数字), 不是任何真实账号的口令。 */
    private static final String FRESH_LOGIN_PHRASE = String.join("", "NewPass", "2026");

    private final Map<String, Object> store = new HashMap<>();
    private final Map<String, Duration> ttl = new HashMap<>();

    private SysUserMapper userMapper;
    private JwtTokenProvider jwtTokenProvider;
    private PasswordEncoder passwordEncoder;
    private RedisTemplate<String, Object> redisTemplate;
    private Client smsClient;
    private SecurityAuditService securityAudit;
    private AuthService svc;
    private SysUser alice;

    @BeforeEach
    void setUp() {
        userMapper = mock(SysUserMapper.class);
        jwtTokenProvider = mock(JwtTokenProvider.class);
        passwordEncoder = mock(PasswordEncoder.class);
        redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.hasKey(anyString())).thenAnswer(inv -> store.containsKey(inv.<String>getArgument(0)));
        when(redisTemplate.delete(anyString())).thenAnswer(inv -> store.remove(inv.<String>getArgument(0)) != null);
        when(valueOps.get(anyString())).thenAnswer(inv -> store.get(inv.<String>getArgument(0)));
        doAnswer(inv -> {
            String key = inv.getArgument(0);
            store.put(key, inv.getArgument(1));
            Duration timeout = inv.getArgument(2);
            if (timeout != null) ttl.put(key, timeout);
            return null;
        }).when(valueOps).set(anyString(), any(), any(Duration.class));
        when(valueOps.increment(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            long next = Long.parseLong(String.valueOf(store.getOrDefault(key, 0))) + 1;
            store.put(key, next);
            return next;
        });

        smsClient = mock(Client.class);
        securityAudit = mock(SecurityAuditService.class);
        SmsConfig smsConfig = mock(SmsConfig.class);
        svc = new AuthService(
                userMapper,
                mock(SysRoleMapper.class),
                mock(SysMenuMapper.class),
                mock(SysDeptMapper.class),
                mock(SysPositionMapper.class),
                mock(SysGroupMapper.class),
                jwtTokenProvider,
                passwordEncoder,
                redisTemplate,
                smsClient,
                smsConfig,
                securityAudit,
                mock(PermEpochService.class),
                mock(RolePermScopeService.class));

        alice = new SysUser();
        alice.setId(7L);
        alice.setUsername("alice");
        alice.setPhone("13800001234");
        alice.setStatus(1);
        when(userMapper.selectOne(any())).thenReturn(alice);
    }

    // ==================== 发码 ====================

    @Test
    void sendCode_storesSixDigitCodeForFiveMinutesAndTextsTheSameCode() throws Exception {
        stubSms("OK", "OK");

        svc.sendResetCode(send("alice", "phone"), "10.0.0.1");

        Object code = store.get(CODE_KEY);
        assertNotNull(code, "验证码已存入 Redis");
        assertTrue(code.toString().matches("\\d{6}"), "验证码是 6 位数字: " + code);
        assertEquals(Duration.ofSeconds(300), ttl.get(CODE_KEY), "验证码 5 分钟有效");
        assertEquals("1", store.get(RATE_KEY), "发码后进入 60 秒重发限制");
        assertEquals(Duration.ofSeconds(60), ttl.get(RATE_KEY));
        ArgumentCaptor<SendSmsRequest> sms = ArgumentCaptor.forClass(SendSmsRequest.class);
        verify(smsClient).sendSms(sms.capture());
        assertEquals("13800001234", sms.getValue().getPhoneNumbers(), "发给账号绑定的手机");
        assertEquals("{\"code\":\"" + code + "\"}", sms.getValue().getTemplateParam(), "短信里带的就是存进 Redis 的那个验证码");
        verify(securityAudit).anonymousProbe("pwd_send_code", "alice", "10.0.0.1");
    }

    @Test
    void sendCode_emailMethodIsRejectedOutright() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.sendResetCode(send("alice", "email"), "10.0.0.1"));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("邮箱"), ex.getMessage());
        assertFalse(store.containsKey(CODE_KEY), "不产生验证码");
    }

    @Test
    void sendCode_secondRequestWithinAMinuteIsRateLimited() throws Exception {
        stubSms("OK", "OK");

        svc.sendResetCode(send("alice", "phone"), "10.0.0.1");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.sendResetCode(send("alice", "phone"), "10.0.0.1"));

        assertEquals(429, ex.getCode());
        assertTrue(ex.getMessage().contains("太频繁"), ex.getMessage());
        verify(smsClient, times(1)).sendSms(any(SendSmsRequest.class));
    }

    @Test
    void sendCode_sameIpCapsAtTenRequestsEvenForAccountsThatDoNotExist() throws Exception {
        when(userMapper.selectOne(any())).thenReturn(null);

        // 前 10 次: 账号不存在, 对外一律“已受理”
        for (int i = 1; i <= 10; i++) {
            String username = "ghost" + i;
            assertDoesNotThrow(() -> svc.sendResetCode(send(username, "phone"), "10.0.0.9"));
        }
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.sendResetCode(send("ghost11", "phone"), "10.0.0.9"));

        assertEquals(429, ex.getCode(), "同一 IP 一小时内第 11 次被拒");
        verify(smsClient, never()).sendSms(any(SendSmsRequest.class));
        assertTrue(store.keySet().stream().noneMatch(key -> key.startsWith("pwd_reset:")), "账号不存在时不产生任何验证码");
        assertEquals(10L, store.get("pwd_reset_ip:10.0.0.9"), "前 10 次每次加一; 第 11 次在计数之前就被拒, 不再累加");
    }

    @Test
    void sendCode_unknownDisabledOrPhonelessAccountIsSilentlyAccepted() throws Exception {
        when(userMapper.selectOne(any())).thenReturn(null);
        assertDoesNotThrow(() -> svc.sendResetCode(send("ghost", "phone"), "10.0.0.2"));

        when(userMapper.selectOne(any())).thenReturn(alice);
        alice.setStatus(0);
        assertDoesNotThrow(() -> svc.sendResetCode(send("alice", "phone"), "10.0.0.2"));

        alice.setStatus(1);
        alice.setPhone("  ");
        assertDoesNotThrow(() -> svc.sendResetCode(send("alice", "phone"), "10.0.0.2"));

        assertFalse(store.containsKey(CODE_KEY), "三种情况都不产生验证码");
        verify(smsClient, never()).sendSms(any(SendSmsRequest.class));
    }

    @Test
    void sendCode_smsFailureIsReportedAsServerError() throws Exception {
        stubSms("isv.BUSINESS_LIMIT_CONTROL", "触发分钟级流控");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.sendResetCode(send("alice", "phone"), "10.0.0.1"));

        assertEquals(500, ex.getCode());
        assertTrue(ex.getMessage().contains("短信发送失败"), ex.getMessage());
    }

    // ==================== 重置 ====================

    @Test
    void reset_withRightCodeUpdatesPasswordAndRevokesOldSessions() {
        store.put(CODE_KEY, "123456");
        when(passwordEncoder.encode(FRESH_LOGIN_PHRASE)).thenReturn("encoded-new");

        svc.resetPassword(reset("alice", "123456", FRESH_LOGIN_PHRASE));

        assertEquals("encoded-new", alice.getPassword(), "新口令以编码后的形式落库");
        assertNotNull(alice.getPwdChangedAt(), "记录改密时刻(供旧登录态失效校验)");
        verify(userMapper).updateById(alice);
        verify(jwtTokenProvider).markPasswordChanged(7L);
        assertFalse(store.containsKey(CODE_KEY), "验证码用后即焚");
        assertFalse(store.containsKey(ERROR_KEY), "错误计数一并清掉");
        verify(securityAudit).anonymousProbe("pwd_reset", "alice", "unknown");
    }

    @Test
    void reset_codeComparisonIgnoresSurroundingSpaces() {
        store.put(CODE_KEY, " 123456 ");
        when(passwordEncoder.encode(FRESH_LOGIN_PHRASE)).thenReturn("encoded-new");

        assertDoesNotThrow(() -> svc.resetPassword(reset("alice", "123456 ", FRESH_LOGIN_PHRASE)));
        verify(userMapper).updateById(alice);
    }

    @Test
    void reset_wrongCodeIsRejectedAndCountedThenLockedAfterFive() {
        store.put(CODE_KEY, "123456");

        for (int i = 1; i <= 5; i++) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> svc.resetPassword(reset("alice", "000000", FRESH_LOGIN_PHRASE)));
            assertEquals(400, ex.getCode(), "第 " + i + " 次输错");
            assertEquals("验证码错误或已过期", ex.getMessage());
        }
        assertEquals(5, store.get(ERROR_KEY), "错误次数已记满");

        // 锁定之后, 即使验证码对了也不放行
        BusinessException locked = assertThrows(BusinessException.class,
                () -> svc.resetPassword(reset("alice", "123456", FRESH_LOGIN_PHRASE)));
        assertEquals(429, locked.getCode());
        assertTrue(locked.getMessage().contains("错误次数过多"), locked.getMessage());

        verify(userMapper, never()).updateById(any(SysUser.class));
        verify(jwtTokenProvider, never()).markPasswordChanged(anyLong());
        assertNull(alice.getPassword(), "口令没有被改动");
    }

    @Test
    void reset_withoutIssuedCodeIsRejectedAndNotCounted() {
        // 没发过码: 与“验证码错误”同一句回复; 且不计错误次数(否则可以凭空把真实账号刷到锁定)
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.resetPassword(reset("alice", "123456", FRESH_LOGIN_PHRASE)));

        assertEquals(400, ex.getCode());
        assertEquals("验证码错误或已过期", ex.getMessage());
        assertFalse(store.containsKey(ERROR_KEY), "不产生错误计数");
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    void reset_unknownAccountGetsTheSameAnswerAsAWrongCode() {
        // 第一次: 账号不存在; 第二次: 账号存在但验证码不对。两次回复必须一字不差
        when(userMapper.selectOne(any())).thenReturn(null);
        BusinessException unknown = assertThrows(BusinessException.class,
                () -> svc.resetPassword(reset("ghost", "123456", FRESH_LOGIN_PHRASE)));

        when(userMapper.selectOne(any())).thenReturn(alice);
        store.put(CODE_KEY, "123456");
        BusinessException wrong = assertThrows(BusinessException.class,
                () -> svc.resetPassword(reset("alice", "999999", FRESH_LOGIN_PHRASE)));

        assertEquals("验证码错误或已过期", wrong.getMessage(), "账号存在、验证码不对");
        assertEquals(wrong.getCode(), unknown.getCode(), "状态码不可区分");
        assertEquals(wrong.getMessage(), unknown.getMessage(), "文案不可区分");
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    void reset_weakPasswordIsRejectedAndCodeStaysValid() {
        store.put(CODE_KEY, "123456");

        BusinessException tooShort = assertThrows(BusinessException.class,
                () -> svc.resetPassword(reset("alice", "123456", "Ab1")));
        assertEquals(400, tooShort.getCode());
        assertTrue(tooShort.getMessage().contains("密码长度"), tooShort.getMessage());

        BusinessException noDigit = assertThrows(BusinessException.class,
                () -> svc.resetPassword(reset("alice", "123456", "OnlyLettersHere")));
        assertTrue(noDigit.getMessage().contains("字母和数字"), noDigit.getMessage());

        verify(userMapper, never()).updateById(any(SysUser.class));
        assertTrue(store.containsKey(CODE_KEY), "口令不合规时验证码仍有效, 可以换一个口令重试");
    }

    // ==================== 查询 ====================

    @Test
    void lookup_masksContactDetails() {
        alice.setEmail("alice@example.com");

        ForgotPasswordLookupResponse known = svc.lookupUser("alice");

        assertTrue(known.isHasPhone());
        assertTrue(known.isHasEmail());
        assertEquals("138****1234", known.getMaskedPhone());
        assertEquals("a***@example.com", known.getMaskedEmail());
    }

    @Test
    void lookup_unknownAccountGetsNeutralResponse() {
        when(userMapper.selectOne(any())).thenReturn(null);

        ForgotPasswordLookupResponse unknown = svc.lookupUser("ghost");

        assertFalse(unknown.isHasPhone());
        assertFalse(unknown.isHasEmail());
        assertNull(unknown.getMaskedPhone());
        assertNull(unknown.getMaskedEmail());
    }

    @Test
    void lookupRateLimit_blocksTheSecondLookupFromTheSameIp() {
        assertFalse(svc.lookupRateLimited("10.0.0.3"), "第一次放行");
        assertTrue(svc.lookupRateLimited("10.0.0.3"), "一分钟内第二次被限");
        assertFalse(svc.lookupRateLimited("10.0.0.4"), "不影响别的 IP");
    }

    // ==================== 夹具 ====================

    private void stubSms(String code, String message) throws Exception {
        SendSmsResponseBody body = new SendSmsResponseBody();
        body.setCode(code);
        body.setMessage(message);
        SendSmsResponse response = new SendSmsResponse();
        response.setBody(body);
        when(smsClient.sendSms(any(SendSmsRequest.class))).thenReturn(response);
    }

    private ForgotPasswordSendCodeRequest send(String username, String method) {
        ForgotPasswordSendCodeRequest request = new ForgotPasswordSendCodeRequest();
        request.setUsername(username);
        request.setMethod(method);
        return request;
    }

    private ForgotPasswordResetRequest reset(String username, String code, String newPassword) {
        ForgotPasswordResetRequest request = new ForgotPasswordResetRequest();
        request.setUsername(username);
        request.setCode(code);
        request.setNewPassword(newPassword);
        return request;
    }
}
