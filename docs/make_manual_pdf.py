from pathlib import Path

from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.cidfonts import UnicodeCIDFont
from reportlab.platypus import PageBreak, Paragraph, SimpleDocTemplate, Spacer


ROOT = Path(__file__).resolve().parent
TXT = ROOT / "罗德岛通讯端用户使用说明书.txt"
PDF = ROOT / "罗德岛通讯端用户使用说明书.pdf"
pdfmetrics.registerFont(UnicodeCIDFont("STSong-Light"))

styles = getSampleStyleSheet()
styles.add(ParagraphStyle(name="ManualTitle", parent=styles["Title"], fontName="STSong-Light",
                          fontSize=26, leading=34, alignment=TA_CENTER, spaceAfter=16))
styles.add(ParagraphStyle(name="ManualSubtitle", parent=styles["Normal"], fontName="STSong-Light",
                          fontSize=13, leading=21, alignment=TA_CENTER, textColor="#4b5563"))
styles.add(ParagraphStyle(name="Chapter", parent=styles["Heading1"], fontName="STSong-Light",
                          fontSize=17, leading=25, spaceBefore=12, spaceAfter=9,
                          textColor="#173b57", keepWithNext=True))
styles.add(ParagraphStyle(name="Section", parent=styles["Heading2"], fontName="STSong-Light",
                          fontSize=12.5, leading=19, spaceBefore=9, spaceAfter=5,
                          textColor="#245b7a", keepWithNext=True))
styles.add(ParagraphStyle(name="BodyCN", parent=styles["BodyText"], fontName="STSong-Light",
                          fontSize=9.5, leading=15.5, spaceAfter=5, wordWrap="CJK"))
styles.add(ParagraphStyle(name="BulletCN", parent=styles["BodyText"], fontName="STSong-Light",
                          fontSize=9.5, leading=15.5, leftIndent=13, firstLineIndent=-8,
                          spaceAfter=3, wordWrap="CJK"))
styles.add(ParagraphStyle(name="SmallCN", parent=styles["BodyText"], fontName="STSong-Light",
                          fontSize=8.5, leading=13, textColor="#4b5563", wordWrap="CJK"))


def esc(text: str) -> str:
    return (text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))


def footer(canvas, doc):
    canvas.saveState()
    canvas.setFont("STSong-Light", 8)
    canvas.setFillColorRGB(0.35, 0.4, 0.45)
    canvas.drawString(18 * mm, 10 * mm, "罗德岛通讯端用户使用说明书")
    canvas.drawRightString(192 * mm, 10 * mm, f"第 {doc.page} 页")
    canvas.restoreState()


def build():
    lines = TXT.read_text(encoding="utf-8").splitlines()
    story = []
    story.extend([
        Spacer(1, 35 * mm),
        Paragraph("罗德岛通讯端", styles["ManualTitle"]),
        Paragraph("用户使用说明书", styles["ManualTitle"]),
        Spacer(1, 8 * mm),
        Paragraph("面向普通用户的完整使用指南", styles["ManualSubtitle"]),
        Spacer(1, 5 * mm),
        Paragraph("覆盖聊天、群聊、记忆、人设、知识库、Galgame、导入导出与数据安全", styles["ManualSubtitle"]),
        PageBreak(),
        Paragraph("使用说明", styles["Chapter"]),
        Paragraph("本手册优先解释功能能解决什么问题，再说明具体操作和注意事项。软件界面文字可能随版本更新而略有变化，请以实际显示为准。", styles["BodyCN"]),
        Spacer(1, 5 * mm),
        Paragraph("目录", styles["Chapter"]),
        Paragraph("一、这是什么软件　　二、第一次使用　　三、模型设置　　四、你的身份与角色人设", styles["BodyCN"]),
        Paragraph("五、私聊　　六、群聊　　七、记忆功能　　八、向量记忆：帮助角色找回久远经历", styles["BodyCN"]),
        Paragraph("九、知识库：给角色增加资料　　十、聊天表现与长度设置　　十一、角色说话规则", styles["BodyCN"]),
        Paragraph("十二、Galgame　　十三、角色卡与聊天记录导出　　十四、完整备份与恢复", styles["BodyCN"]),
        Paragraph("十五、数据管理与隐私　　十六、常见问题　　十七、名词解释", styles["BodyCN"]),
        PageBreak(),
    ])

    for line in lines:
        text = line.strip()
        if not text or text.startswith("====") or text.startswith("版本：") or text.startswith("适用对象："):
            continue
        if text == "罗德岛通讯端" or text == "用户使用说明书" or text == "目录":
            continue
        chapter_prefixes = ("一、", "二、", "三、", "四、", "五、", "六、", "七、", "八、", "九、", "十、", "十一、", "十二、", "十三、", "十四、", "十五、", "十六、", "十七、")
        if text.startswith(chapter_prefixes) or (len(text) > 1 and text[0].isdigit() and text[1] == "、"):
            story.append(Paragraph(esc(text), styles["Chapter"]))
            continue
        if len(text) >= 3 and text[0].isdigit() and text[1] == ".":
            story.append(Paragraph(esc(text), styles["Section"]))
            continue
        if text.startswith("问：") or text.startswith("答："):
            story.append(Paragraph(esc(text), styles["BodyCN"]))
            continue
        if text.startswith("• ") or text.startswith("□ "):
            story.append(Paragraph(esc(text), styles["BulletCN"]))
            continue
        story.append(Paragraph(esc(text), styles["BodyCN"]))

    doc = SimpleDocTemplate(str(PDF), pagesize=A4, rightMargin=18 * mm, leftMargin=18 * mm,
                            topMargin=17 * mm, bottomMargin=17 * mm, title="罗德岛通讯端用户使用说明书",
                            author="罗德岛通讯端")
    doc.multiBuild(story, onFirstPage=footer, onLaterPages=footer)


if __name__ == "__main__":
    build()
