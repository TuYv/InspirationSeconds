package com.example.wxnotion.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
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

    // 色彩配置
    private static final Color BG_COLOR = new Color(0xF9, 0xF7, 0xF2); // 米白色
    private static final Color TEXT_PRIMARY = new Color(0x33, 0x33, 0x33); // 炭灰
    private static final Color TEXT_ACCENT = new Color(0xB8, 0x5C, 0x38); // 赤陶色
    private static final Color TAG_BG = new Color(0xE0, 0xDC, 0xD5); // 标签底色 (加深一点，提高对比度)

    /**
     * 加载字体 (支持降级)
     */
    private static Font loadFont(String fontFileName, int style, float size) {
        try {
            java.net.URL fontUrl = ImageGenerator.class.getClassLoader().getResource("fonts/" + fontFileName);
            if (fontUrl != null) {
                Font font = Font.createFont(Font.TRUETYPE_FONT, fontUrl.openStream());
                return font.deriveFont(style, size);
            }
        } catch (Exception e) {
            log.warn("加载字体 {} 失败，使用默认字体: {}", fontFileName, e.getMessage());
        }
        // 降级字体
        String fallbackName = (style == Font.BOLD) ? "SansSerif" : "Serif";
        return new Font(fallbackName, style, (int)size);
    }

    public static File generateDailyCard(String yesterdaySummary, String todayQuote, String keywords) throws IOException {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();

        // 1. 开启顶级抗锯齿
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // 2. 填充背景
        g2.setColor(BG_COLOR);
        g2.fillRect(0, 0, WIDTH, HEIGHT);

        // 加载字体
        Font titleFont = loadFont("NotoSerifSC-Bold.otf", Font.BOLD, 140);
        Font subTitleFont = loadFont("Roboto-Regular.ttf", Font.PLAIN, 24); 
        Font sectionTitleFont = loadFont("NotoSerifSC-Bold.otf", Font.BOLD, 18); 
        Font bodyFont = loadFont("NotoSerifSC-Regular.otf", Font.PLAIN, 28);
        Font quoteFont = loadFont("NotoSerifSC-Bold.otf", Font.PLAIN, 48);
        Font tagFont = loadFont("NotoSerifSC-Regular.otf", Font.PLAIN, 20);

        // --- 绘制头部 ---
        int cursorY = 130;
        int margin = 40;
        int maxTextWidth = WIDTH - margin * 2;

        // 大日历数字 "22"
        LocalDate now = LocalDate.now();
        g2.setColor(TEXT_ACCENT);
        g2.setFont(titleFont);
        g2.drawString(String.valueOf(now.getDayOfMonth()), margin, cursorY);
        
        // 年月星期
        g2.setColor(TEXT_PRIMARY);
        g2.setFont(subTitleFont);
        String yearMonth = now.format(DateTimeFormatter.ofPattern("yyyy.MM"));
        String dayOfWeek = now.getDayOfWeek().toString();
        g2.drawString(yearMonth + " / " + dayOfWeek, margin + 180, cursorY);

        // --- 绘制分割线 ---
        cursorY += 40;
        g2.setColor(new Color(0, 0, 0, 30)); // 极淡的分割线
        g2.drawLine(margin, cursorY, WIDTH - margin, cursorY);

        // --- 绘制昨日回响 ---
        cursorY += 40;
        drawSectionTitle(g2, "昨日回响 / REVIEW", margin, cursorY, sectionTitleFont);
        
        cursorY += 40;
        if (yesterdaySummary != null && !yesterdaySummary.isEmpty()) {
            g2.setColor(TEXT_PRIMARY);
            g2.setFont(bodyFont);
            cursorY = drawMultilineText(g2, yesterdaySummary, margin, cursorY, maxTextWidth, 50);
        }

        // --- 绘制今日启示 (重点区域) ---
        
        // 清洗引言内容
        String cleanQuote = (todayQuote != null) ? todayQuote.replaceAll("[“”\"']", "").trim() : "";
        
        // 动态调整字体大小和高度
        // 目标：确保底部至少留出 140px (120px for QR + 20px buffer)
        int minBottomSpace = 140;
        //当前可剩余的空间
        int maxHeight = HEIGHT - cursorY - minBottomSpace;
        //当前字体
        int currentQuoteFontSize = 48;
        //字体行高
        int quoteLineHeight = 65;
        //总字体高度
        int textHeight = 0;

        List<String> lineList = Arrays.stream(cleanQuote.split("\n")).filter(StringUtils::isNotBlank).toList();
        // 自适应循环：如果高度不够，就缩小字体
        while (currentQuoteFontSize >= 24) {
            // 重新计算行高 (大概是字号的 1.4 倍)
            quoteLineHeight = (int)(currentQuoteFontSize * 1.4);

            // 检查是否有单行宽度超出 (禁止自动换行模式下)
            FontMetrics fm = g2.getFontMetrics();
            int maxAllowedWidth = maxTextWidth - 60;
            //判断是否有单行宽度超出
            boolean widthOverflow = lineList.stream().map(fm::stringWidth).anyMatch(w -> w > maxAllowedWidth);
            // 计算高度
            textHeight = lineList.size() * quoteLineHeight + 110;
            
            if (!widthOverflow && (textHeight <= maxHeight || textHeight <= 240)) {
                break;
            }
            currentQuoteFontSize -= 2; // 每次缩小 2px
        }
        quoteFont = quoteFont.deriveFont((float)currentQuoteFontSize);
        g2.setFont(quoteFont);
        
        int cardHeight = Math.max(200, textHeight);
        
        // 1. 绘制引言卡片阴影
        g2.setColor(new Color(0, 0, 0, 15));
        g2.fillRoundRect(margin + 5, cursorY + 5, maxTextWidth, cardHeight, 20, 20);

        // 2. 绘制引言卡片背景
        g2.setColor(new Color(255, 255, 255));
        g2.fillRoundRect(margin, cursorY, maxTextWidth, cardHeight, 20, 20);
        
        // 3. 绘制装饰性巨型引号
        g2.setColor(new Color(0xE0, 0xE0, 0xE0));
        g2.setFont(new Font("Georgia", Font.BOLD, 120)); 
        g2.drawString("“", margin + 20, cursorY + 100); 
        
        // 4. 绘制引言内容
        if (!cleanQuote.isEmpty()) {
            g2.setColor(TEXT_PRIMARY);
            g2.setFont(quoteFont); // 使用最终确定的大小的字体
            int quoteY = cursorY + 110;
            drawCenteredWrappedText(g2, lineList, quoteY, quoteLineHeight);
        }

        // --- 底部：标签与二维码 ---
        int bottomY = HEIGHT - 140;

        // 绘制标签 (Pill shape)
        if (keywords != null && !keywords.isEmpty()) {
            String[] tags = keywords.split(" ");
            int tagX = margin;
            g2.setFont(tagFont);
            FontMetrics fmTag = g2.getFontMetrics();
            int tagAscent = fmTag.getAscent();

            for (String tag : tags) {
                if (tag.isEmpty()) continue;
                String cleanTag = tag.startsWith("#") ? tag : "#" + tag;

                int textWidth = fmTag.stringWidth(cleanTag);
                int padding = 20;
                int tagHeight = 44;

                // 检查是否超出二维码区域左侧 (简单保护)
                if (tagX + textWidth + padding * 2 > WIDTH - margin - 120) {
                    break; // 空间不足，停止绘制标签
                }

                // 标签背景
                g2.setColor(TAG_BG);
                g2.fillRoundRect(tagX, bottomY, textWidth + padding * 2, tagHeight, tagHeight, tagHeight);

                // 标签文字
                g2.setColor(new Color(0x55, 0x55, 0x55));
                int textY = (bottomY) + (tagHeight - fmTag.getHeight()) / 2 + tagAscent;
                g2.drawString(cleanTag, tagX + padding, textY + 2);

                tagX += textWidth + padding * 2 + 15;
            }
        }
        
        // 绘制二维码
        int qrSize = 100; 
        int qrX = WIDTH - margin - qrSize; 
        int qrY = HEIGHT - 140;
        
        try {
             java.net.URL qrCodeUrl = ImageGenerator.class.getClassLoader().getResource("static/images/qrcode.png");
             if (qrCodeUrl != null) {
                 BufferedImage qr = ImageIO.read(qrCodeUrl);
                 g2.drawImage(qr, qrX, qrY, qrSize, qrSize, null);
             } else {
                 log.warn("二维码资源未找到");
             }
        } catch (Exception e) {
             log.warn("二维码加载失败: {}", e.getMessage());
        }
        
        // 扫码文字
        g2.setFont(new Font("SansSerif", Font.PLAIN, 12)); 
        g2.setColor(Color.GRAY);
        String slogan = "捕捉瞬间灵感";
        FontMetrics fm = g2.getFontMetrics();
        int sloganWidth = fm.stringWidth(slogan);
        // Slogan 居中对齐于二维码
        g2.drawString(slogan, qrX + (qrSize - sloganWidth) / 2, qrY + qrSize + 20);

        g2.dispose();

        File file = new File(TEMP_DIR, "daily_card_" + UUID.randomUUID() + ".jpg");
        ImageIO.write(image, "jpg", file);
        log.info("日签图片已生成: {}", file.getAbsolutePath());
        return file;
    }
    
    // 辅助方法：绘制小标题
    private static void drawSectionTitle(Graphics2D g2, String title, int x, int y, Font font) {
        g2.setColor(new Color(0x99, 0x99, 0x99)); // 浅灰色标题
        g2.setFont(font);
        // Java原生Graphics2D调整字间距比较麻烦，这里简单绘制
        g2.drawString(title, x, y);
    }
    
    // 辅助方法：多行文字绘制 (左对齐) - 支持换行符
    private static int drawMultilineText(Graphics2D g, String text, int x, int y, int maxWidth, int lineHeight) {
        FontMetrics m = g.getFontMetrics();
        if (text == null || text.isEmpty()) return y;
        
        String[] paragraphs = text.split("\n"); // 按段落分割
        int curY = y;
        
        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                continue;
            }
            String[] words = paragraph.split(""); 
            StringBuilder currentLine = new StringBuilder();
            
            for (String word : words) {
                if (m.stringWidth(currentLine + word) >= maxWidth) {
                    g.drawString(currentLine.toString(), x, curY);
                    curY += lineHeight;
                    currentLine = new StringBuilder();
                }
                currentLine.append(word);
            }
            if (currentLine.length() > 0) {
                g.drawString(currentLine.toString(), x, curY);
                curY += lineHeight;
            }
        }
        return curY;
    }
    
    /**
     * 居中绘制自动换行的文本
     */
    private static void drawCenteredWrappedText(Graphics2D g2, List<String> text, int curY, int lineHeight) {
        FontMetrics fm = g2.getFontMetrics();
        for (int i = 0; i < text.size(); i++) {
            String paragraph = text.get(i);
            int lineWidth = fm.stringWidth(paragraph);
            if ( i + 1 == text.size()) {
                g2.drawString(paragraph, WIDTH - lineWidth - 50, curY);
            } else {
                g2.drawString(paragraph, (WIDTH - lineWidth) / 2, curY);
            }
            curY += lineHeight;
        }
    }
}
