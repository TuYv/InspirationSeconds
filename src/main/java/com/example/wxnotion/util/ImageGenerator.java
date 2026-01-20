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
    private static final int PADDING = 80;
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
        int currentY = 260;
        int maxTextWidth = WIDTH - 2 * PADDING;
        
        // --- 5.1 昨日回响 (左对齐) ---
        if (yesterdaySummary != null && !yesterdaySummary.isEmpty()) {
            // 标题 (小字号，灰色)
            g2.setColor(new Color(120, 120, 120));
            g2.setFont(new Font("Serif", Font.BOLD, 20));
            g2.drawString("##📝 昨日回响", PADDING, currentY);
            currentY += 40;
            
            // 内容 (标准字号，深灰，左对齐)
            g2.setColor(new Color(60, 60, 60));
            g2.setFont(new Font("SansSerif", Font.PLAIN, 26));
            // 绘制内容 (左对齐绘制)
            currentY = drawWrappedText(g2, yesterdaySummary, PADDING, currentY, maxTextWidth, 40);
            currentY += 60; // 段落间距
        }
        
        // --- 5.2 今日启示 (居中) ---
        if (todayQuote != null && !todayQuote.isEmpty()) {
            // 标题 (小字号，灰色，居中)
            g2.setColor(new Color(120, 120, 120));
            g2.setFont(new Font("Serif", Font.BOLD, 20));
            FontMetrics fm = g2.getFontMetrics();
            String title = "##🔮 今日启示";
            g2.drawString(title, PADDING, currentY);
            currentY += 60;
            
            // 内容 (大字号，黑色，居中)
            g2.setColor(new Color(30, 30, 30));
            g2.setFont(new Font("SansSerif", Font.BOLD, 32)); // 加粗
            currentY = drawCenteredWrappedText(g2, todayQuote, WIDTH / 2, currentY, maxTextWidth, 50);
        }

        // 6. 底部区域 (左Tag，右二维码+Slogan)
        int footerY = HEIGHT - 230;
        
        // 左下角：Tags
        if (keywords != null && !keywords.isEmpty()) {
            g2.setColor(new Color(100, 100, 150));
            g2.setFont(new Font("SansSerif", Font.ITALIC, 24));
            // 简单处理 Tag 换行或截断 (这里假设 Tag 不会太长)
            g2.drawString(keywords, PADDING, footerY + 80);
        }
        
        // 右下角：二维码 + Slogan
        int qrSize = 200;
        int qrX = WIDTH - PADDING - qrSize;
        int qrY = footerY;
        
        if (qrCodePath != null) {
             try {
                 File qrFile = new File(qrCodePath);
                 if (qrFile.exists()) {
                     BufferedImage qr = ImageIO.read(qrFile);
                     g2.drawImage(qr, qrX, qrY, qrSize, qrSize, null);
                 }
             } catch (Exception e) {
                 log.warn("二维码加载失败: {}", e.getMessage());
             }
        }
        
        // Slogan (二维码下方)
        g2.setColor(new Color(100, 100, 100));
        g2.setFont(new Font("Serif", Font.PLAIN, 16));
        String slogan = "捕捉瞬间灵感";
        FontMetrics fm = g2.getFontMetrics();
        int sloganWidth = fm.stringWidth(slogan);
        // Slogan 居中对齐于二维码
        g2.drawString(slogan, qrX + (qrSize - sloganWidth) / 2, qrY + qrSize + 25);

        g2.dispose();

        File file = new File(TEMP_DIR, "daily_card_" + UUID.randomUUID() + ".jpg");
        ImageIO.write(image, "jpg", file);
        log.info("日签图片已生成: {}", file.getAbsolutePath());
        return file;
    }
    
    /**
     * 左对齐绘制自动换行的文本
     */
    private static int drawWrappedText(Graphics2D g2, String text, int x, int y, int maxWidth, int lineHeight) {
        FontMetrics fm = g2.getFontMetrics();
        String[] words = text.split(""); 
        StringBuilder line = new StringBuilder();
        int curY = y;

        for (String word : words) {
            if (fm.stringWidth(line + word) < maxWidth) {
                line.append(word);
            } else {
                g2.drawString(line.toString(), x, curY);
                line = new StringBuilder(word);
                curY += lineHeight;
            }
        }
        if (line.length() > 0) {
            g2.drawString(line.toString(), x, curY);
        }
        return curY + lineHeight; // 返回下一行的 Y 坐标
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
