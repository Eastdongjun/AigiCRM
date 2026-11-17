package com.aigicrm.module.file.service;

import com.aigicrm.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/** 【2026-11-17 P2-3】EICAR 全文件扫描：签名在 4KB 之后也不能绕过。 */
class FileScanServiceTest {

    @TempDir
    Path tmp;

    private static final byte[] EICAR =
            "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*"
                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    @Test
    void detectsSignatureBeyondFirst4K() throws Exception {
        Path f = tmp.resolve("tail.bin");
        byte[] prefix = new byte[5000];
        java.util.Arrays.fill(prefix, (byte) 'A');
        byte[] content = new byte[prefix.length + EICAR.length];
        System.arraycopy(prefix, 0, content, 0, prefix.length);
        System.arraycopy(EICAR, 0, content, prefix.length, EICAR.length);
        Files.write(f, content);
        assertThrows(BusinessException.class, () -> new FileScanService().scan(f));
    }

    @Test
    void cleanFilePasses() throws Exception {
        Path f = tmp.resolve("clean.txt");
        Files.writeString(f, "hello world");
        assertDoesNotThrow(() -> new FileScanService().scan(f));
    }
}
