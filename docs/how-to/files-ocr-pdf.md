---
title: OCR, QR codes, PDF and files
sidebar_position: 17
---

# OCR, QR codes, PDF and files

```sel title="documents.sel"
open chrome
goto https://shop.example.com/invoice/42
ocr id@captcha captchaText
qrcode css@img.ticket ticketCode
printpdf '/tmp/invoice-42.pdf' text
readfile "/tmp/invoice-42.pdf.txt"
{
	exec @print(@line)
}
```

| Statement | Does |
|---|---|
| `ocr <element> <var> [image-file]` (also `readimgtext`) | Reads the text of an element's image with Tesseract into a variable, for example a captcha |
| `qrcode <element> <var>`, `barcode …`, `readqrbar …` | Decodes a QR or bar code shown in the page (ZXing) |
| `printpdf <file> [text]` | Prints the page to PDF (CDP on Chromium, BiDi on Firefox, or W3C print, locally or on a Grid); with `text`, also writes the text and tables to `<file>.txt`. If the page shows a PDF, it saves that document |
| `wopensave on` … `wopensave off <file> [text]` | Saves documents the page opens with `window.open` |
| `readfile <file> [binary]` | Loops over a file's lines (`@line`, `@index`); write it without `#` in 5.0.0 |

Embedded Java code can read PDF tables with `pdfTable(path)`. Element and full-page screenshots are in [Screenshots, OCR, QR/barcode & PDF](../seleasy/REFERENCE.md#screenshots-ocr-qrbarcode--pdf).
