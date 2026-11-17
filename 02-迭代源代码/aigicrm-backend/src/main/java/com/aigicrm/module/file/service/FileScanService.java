package com.aigicrm.module.file.service;

import com.aigicrm.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * 【2026-11-17 阶段三】上传病毒扫描钩子：
 * 1) 永远执行 EICAR 测试串检查（零依赖基线，命中即拒绝并删除落盘文件）；
 * 2) 配置 file.scan.command 后调用外部扫描器（如 clamdscan），非 0 退出码=拒绝（fail-closed）。
 * 默认不启用外部扫描，部署时按环境配置；扫描失败绝不把文件写入台账。
 */
@Service
public class FileScanService {

    @Value("${file.scan.enabled:false}")
    private boolean enabled;
    @Value("${file.scan.command:}")
    private String command;
    @Value("${file.scan.timeout-seconds:60}")
    private int timeoutSeconds;

    private static final byte[] EICAR = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*"
            .getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    public void scan(Path file) {
        if (file == null || !Files.isRegularFile(file)) return;
        try {
            if (containsEicar(file)) throw new BusinessException("文件未通过安全扫描（EICAR 测试串）");
            if (!enabled) return;
            if (command == null || command.isBlank()) {
                throw new BusinessException("病毒扫描已启用但未配置 file.scan.command");
            }
            // 将路径作为最后一个参数（命令模板不含路径时也安全）
            ProcessBuilder pb = new ProcessBuilder();
            pb.command().addAll(java.util.List.of(command.trim().split("\\s+")));
            pb.command().add(file.toString());
            Process real = pb.redirectErrorStream(true).start();
            real.getOutputStream().close();
            boolean done = real.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!done) { real.destroyForcibly(); throw new BusinessException("病毒扫描超时，文件已拒绝"); }
            if (real.exitValue() != 0) throw new BusinessException("文件未通过安全扫描");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("病毒扫描失败，文件已拒绝: " + e.getMessage());
        }
    }

    private boolean containsEicar(Path file) throws Exception {
        byte[] buf = new byte[4096];
        int n;
        try (InputStream in = Files.newInputStream(file)) {
            n = in.read(buf);
        }
        if (n <= 0) return false;
        outer:
        for (int i = 0; i + EICAR.length <= n; i++) {
            for (int j = 0; j < EICAR.length; j++) if (buf[i + j] != EICAR[j]) continue outer;
            return true;
        }
        return false;
    }
}
