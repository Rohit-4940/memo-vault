package com.nexmemo.service.document;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * @author : Rohit Chapagain
 * @created : 2026-05-10, Sunday
 *
 * Tesseract 5 OCR via Tess4J — Apache 2.0, free forever.
 * Supports English (eng) + Nepali/Devanagari (nep).
 *
 * ── WINDOWS SETUP ────────────────────────────────────────────────────────────
 * 1. Download Tesseract installer:
 *    https://github.com/UB-Mannheim/tesseract/wiki
 *    Install to: C:\Program Files\Tesseract-OCR\
 *    Tick "Additional language data" → select Nepali during install
 *
 * 2. Create tessdata folder: C:\nexmemo\tessdata\
 *
 * 3. Download language files manually if not installed:
 *    eng.traineddata → https://github.com/tesseract-ocr/tessdata/raw/main/eng.traineddata
 *    nep.traineddata → https://github.com/tesseract-ocr/tessdata/raw/main/nep.traineddata
 *    Place both in: C:\nexmemo\tessdata\
 *
 * 4. Add to PATH: C:\Program Files\Tesseract-OCR\
 *
 * 5. application-dev.yml:
 *    nexmemo.ocr.tessdata-path: C:/nexmemo/tessdata
 *    nexmemo.ocr.language: eng+nep
 *
 * ── ALSO REQUIRED: Native DLLs ───────────────────────────────────────────────
 * Tess4J needs native DLLs on Windows. Two options:
 *
 * Option A (recommended for dev — easiest):
 *   Add to nexmemo-service/pom.xml:
 *   <dependency>
 *     <groupId>net.sourceforge.tess4j</groupId>
 *     <artifactId>tess4j</artifactId>
 *     <version>5.11.0</version>
 *   </dependency>
 *   Tess4J bundles the DLLs automatically inside the jar.
 *   They are extracted to java.io.tmpdir at runtime.
 *
 * Option B (production Linux server):
 *   apt-get install -y tesseract-ocr tesseract-ocr-eng tesseract-ocr-nep
 *   tessdata-path: /usr/share/tesseract-ocr/5/tessdata
 **/
@Slf4j
@Service
public class TesseractOcrService {

/*    @Value("${nexmemo.ocr.tessdata-path:C:/nexmemo/tessdata}")
    private String tessdataPath;

    @Value("${nexmemo.ocr.language:eng+nep}")
    private String language;

    @Value("${nexmemo.ocr.dpi:300}")
    private int dpi;

    *//**
     * Extracts text from image bytes using Tesseract.
     *
     * @param imageBytes  raw bytes of a PNG/JPEG/TIFF image
     * @param contentType MIME type (used for logging only)
     * @return OcrResult with extracted text, confidence score, and engine name
     * @throws TesseractException if OCR fails
     * @throws IOException        if image bytes cannot be decoded
     *//*
    public OcrResult extractText(byte[] imageBytes, String contentType)
            throws TesseractException, IOException {

        // Build Tesseract instance (not thread-safe — create per call)
        ITesseract tesseract = buildTesseract();

        // Decode image bytes → BufferedImage
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (image == null) {
            throw new TesseractException(
                    "Cannot decode image bytes as " + contentType +
                            ". Ensure the file is a valid PNG, JPEG, or TIFF.");
        }

        long startMs = System.currentTimeMillis();

        // Run OCR — throws TesseractException if tessdata path is wrong
        String rawText;
        try {
            rawText = tesseract.doOCR(image);
        } catch (TesseractException e) {
            log.error("Tesseract OCR failed. Check tessdata-path={} language={}: {}",
                    tessdataPath, language, e.getMessage());
            throw e;
        }

        long elapsedMs    = System.currentTimeMillis() - startMs;
        String cleanText  = cleanText(rawText);
        double confidence = estimateConfidence(rawText);

        // SLF4J does NOT support {:.1f} — use String.format for float formatting
        log.info("OCR completed: lang={} chars={} confidence={}% time={}ms",
                language,
                cleanText.length(),
                String.format("%.1f", confidence),
                elapsedMs);

        return new OcrResult(cleanText, confidence, "TESSERACT_5");
    }

    *//**
     * Builds and configures a Tesseract instance.
     * ITesseract is NOT thread-safe — always create a new instance per call.
     *//*
    private ITesseract buildTesseract() {
        Tesseract t = new Tesseract();

        // Path to the folder containing eng.traineddata, nep.traineddata etc.
        t.setDatapath(tessdataPath);

        // Language(s): "eng" = English, "nep" = Nepali, "eng+nep" = both
        t.setLanguage(language);

        // PSM 3 = Fully automatic page segmentation (best for memos and documents)
        // Other options: PSM 6 = assume uniform block of text, PSM 11 = sparse text
        t.setPageSegMode(3);

        // OEM 1 = LSTM neural engine only (most accurate in Tesseract 5)
        // OEM 0 = Legacy engine, OEM 3 = Default (auto-selects)
        t.setOcrEngineMode(1);

        // Keep spaces between words — important for bank memo formatting
        t.setVariable("preserve_interword_spaces", "1");

        // Improves accuracy on noisy/scanned documents
        t.setVariable("textord_heavy_nr", "1");

        // Output digits more accurately (important for amounts in bank memos)
        t.setVariable("classify_bln_numeric_mode", "0");

        return t;
    }

    *//**
     * Normalises whitespace and line endings in extracted text.
     *//*
    private String cleanText(String raw) {
        if (raw == null) return "";
        return raw
                .replaceAll("\\r\\n|\\r", "\n")      // normalise Windows line endings
                .replaceAll("[ \\t]{2,}", " ")         // collapse multiple spaces/tabs
                .replaceAll("\\n{3,}", "\n\n")         // collapse 3+ blank lines to 2
                .trim();
    }

    *//**
     * Estimates OCR confidence as ratio of printable characters.
     *
     * For production: enable HOCR output to get Tesseract's own word-level
     * confidence scores (0-100 per word), then average them:
     *
     *   t.setHocr(true);
     *   String hocr = t.doOCR(image);
     *   // parse hocr XML, extract x_wconf="85" attributes, average them
     *//*
    private double estimateConfidence(String text) {
        if (text == null || text.isBlank()) return 0.0;
        long total     = text.length();
        long printable = text.chars()
                .filter(c -> !Character.isISOControl(c) || c == '\n' || c == '\t')
                .count();
        return (double) printable / total * 100.0;
    }

    *//**
     * OCR result record.
     *
     * @param text       cleaned extracted text
     * @param confidence 0.0–100.0 confidence estimate
     * @param engine     engine name e.g. "TESSERACT_5"
     *//*
    public record OcrResult(String text, double confidence, String engine) {}*/
}
