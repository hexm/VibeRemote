package com.example.lightscript.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * 屏幕截图服务（Windows / macOS）
 * 依赖 java.awt.Robot，需要有效的桌面会话。
 * macOS 需要"屏幕录制"权限，未授权时截图返回黑屏。
 */
class ScreenCaptureService {

    private static final Logger logger = LoggerFactory.getLogger(ScreenCaptureService.class);
    private static final float JPEG_QUALITY = 0.6f;

    private final AgentApi api;
    private final String agentId;
    private final String agentToken;

    private volatile boolean running = false;
    private volatile int intervalSeconds = 3;
    private Thread captureThread;
    private Robot robot;

    ScreenCaptureService(AgentApi api, String agentId, String agentToken) {
        this.api = api;
        this.agentId = agentId;
        this.agentToken = agentToken;
        try {
            this.robot = new Robot();
        } catch (AWTException e) {
            logger.warn("[Screen] Failed to create Robot (no display?): {}", e.getMessage());
            this.robot = null;
        }
    }

    /**
     * 由 AgentMain 心跳循环调用，根据服务器下发的 interval 启停截图线程
     */
    synchronized void updateInterval(int newInterval) {
        if (newInterval > 0 && !running) {
            intervalSeconds = newInterval;
            start();
        } else if (newInterval == 0 && running) {
            stop();
        } else if (newInterval > 0) {
            intervalSeconds = newInterval; // 动态调整频率
        }
    }

    private void start() {
        if (robot == null) {
            logger.warn("[Screen] Cannot start capture: Robot not available");
            return;
        }
        running = true;
        captureThread = new Thread(this::captureLoop, "screen-capture");
        captureThread.setDaemon(true);
        captureThread.start();
        logger.info("[Screen] Capture started, interval={}s", intervalSeconds);
    }

    synchronized void stop() {
        running = false;
        if (captureThread != null) {
            captureThread.interrupt();
            captureThread = null;
        }
        logger.info("[Screen] Capture stopped");
    }

    private void captureLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                boolean shouldContinue = captureAndUpload();
                if (!shouldContinue) {
                    logger.info("[Screen] Server has no active viewer, stopping capture");
                    running = false;
                    break;
                }
                Thread.sleep(intervalSeconds * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.warn("[Screen] Capture error: {}", e.getMessage());
                try { Thread.sleep(5000); } catch (InterruptedException ie) { break; }
            }
        }
    }

    private boolean captureAndUpload() {
        try {
            // 获取主屏幕尺寸
            GraphicsDevice screen = GraphicsEnvironment
                    .getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice();
            Rectangle bounds = screen.getDefaultConfiguration().getBounds();

            // 截图
            BufferedImage image = robot.createScreenCapture(bounds);

            // JPEG 压缩
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(image, null, null), param);
            } finally {
                writer.dispose();
            }

            // Base64 编码
            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());

            // 上传，返回服务器是否要求继续
            return api.uploadScreen(agentId, agentToken, base64);

        } catch (Exception e) {
            logger.warn("[Screen] captureAndUpload failed: {}", e.getMessage());
            return true; // 上传失败不主动停止，等 pull 通道的指令
        }
    }
}
