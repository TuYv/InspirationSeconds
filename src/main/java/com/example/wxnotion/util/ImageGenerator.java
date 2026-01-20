package com.example.wxnotion.util;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 每日日签图片生成器
 * 用于生成包含金句、关键词的高颜值图片，方便用户分享朋友圈
 */
@Slf4j
public class ImageGenerator {

    private static final int WIDTH = 800;
    private static final int HEIGHT = 1000;
    private static final int PADDING = 60;
    // 临时文件目录
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");

    /**
     * 生成日签图片
     * @param yesterdaySummary 昨日回响
     * @param todayQuote 今日启示
     * @param keywords 关键词
     * @param qrCodePath 公众号二维码本地路径
     */
    public static File generateDailyCard(String yesterdaySummary, String todayQuote, String keywords, String qrCodePath) throws IOException {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();

        // 1. 设置抗锯齿
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 2. 背景
        g2.setColor(new Color(250, 249, 246)); 
        g2.fillRect(0, 0, WIDTH, HEIGHT);

        // 3. 顶部日期与头像
        g2.setColor(new Color(50, 50, 50));
        g2.setFont(new Font("Serif", Font.BOLD, 48));
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
        g2.drawString(dateStr, PADDING, 120);
        
        g2.setFont(new Font("Serif", Font.PLAIN, 24));
        String weekStr = LocalDate.now().getDayOfWeek().toString();
        g2.drawString(weekStr, PADDING, 160);
        
        // 4. 绘制天气 (右上角，日期右边)

        // 4. 分割线
        g2.setColor(new Color(200, 200, 200));
        g2.setStroke(new BasicStroke(1));
        g2.drawLine(PADDING, 200, WIDTH - PADDING, 200);

        // 5. 正文内容绘制区域
        int currentY = 400;
        int maxTextWidth = WIDTH - 2 * PADDING;
        
        // --- 5.1 昨日回响 ---
        if (yesterdaySummary != null && !yesterdaySummary.isEmpty()) {
            g2.setColor(new Color(30, 30, 30));
            g2.setFont(new Font("SansSerif", Font.PLAIN, 36));
            // 绘制内容
            currentY = drawCenteredWrappedText(g2, yesterdaySummary, WIDTH / 2, currentY, maxTextWidth, 60);
            currentY += 100; // 段落间距
        }
        
        // --- 5.2 今日启示 ---
        if (todayQuote != null && !todayQuote.isEmpty()) {
            // 内容 (字体稍大)
            g2.setColor(new Color(30, 30, 30));
            g2.setFont(new Font("SansSerif", Font.ITALIC, 32)); // 斜体更有金句感
            currentY = drawCenteredWrappedText(g2, todayQuote, WIDTH / 2, currentY, maxTextWidth, 50);
        }

        // 6. 关键词
        if (keywords != null && !keywords.isEmpty()) {
            g2.setColor(new Color(100, 100, 150));
            g2.setFont(new Font("SansSerif", Font.BOLD, 26));
            FontMetrics fm = g2.getFontMetrics();
            int kwWidth = fm.stringWidth(keywords);
            // 放在二维码上方
            g2.drawString(keywords, (WIDTH - kwWidth) / 2, HEIGHT - 220);
        }
        
        // 7. 底部二维码
        if (qrCodePath != null) {
             try {
                 File qrFile = new File(qrCodePath);
                 if (qrFile.exists()) {
                     BufferedImage qr = ImageIO.read(qrFile);
                     int qrSize = 120;
                     int qrX = (WIDTH - qrSize) / 2;
                     int qrY = HEIGHT - 180;
                     g2.drawImage(qr, qrX, qrY, qrSize, qrSize, null);
                 }
             } catch (Exception e) {
                 log.warn("二维码加载失败: {}", e.getMessage());
             }
        }

        // 8. Slogan
        g2.setColor(new Color(100, 100, 100));
        g2.setFont(new Font("Serif", Font.ITALIC, 18));
        String slogan = "捕捉瞬间灵感";
        FontMetrics fm = g2.getFontMetrics();
        int sloganWidth = fm.stringWidth(slogan);
        g2.drawString(slogan, (WIDTH - sloganWidth) / 2, HEIGHT - 30);

        g2.dispose();

        File file = new File(TEMP_DIR, "daily_card_" + UUID.randomUUID() + ".jpg");
        ImageIO.write(image, "jpg", file);
        log.info("日签图片已生成: {}", file.getAbsolutePath());
        return file;
    }
    
    /**
     * 居中绘制自动换行的文本，返回绘制结束后的 Y 坐标
     */
    private static int drawCenteredWrappedText(Graphics2D g2, String text, int centerX, int y, int maxWidth, int lineHeight) {
        FontMetrics fm = g2.getFontMetrics();
        String[] words = text.split(""); 
        StringBuilder line = new StringBuilder();
        int curY = y;

        for (String word : words) {
            if (fm.stringWidth(line + word) < maxWidth) {
                line.append(word);
            } else {
                String lineStr = line.toString();
                int lineWidth = fm.stringWidth(lineStr);
                g2.drawString(lineStr, centerX - lineWidth / 2, curY);
                line = new StringBuilder(word);
                curY += lineHeight;
            }
        }
        if (line.length() > 0) {
            String lineStr = line.toString();
            int lineWidth = fm.stringWidth(lineStr);
            g2.drawString(lineStr, centerX - lineWidth / 2, curY);
            curY += lineHeight; // 加上最后一行的行高
        }
        return curY;
    }
}
