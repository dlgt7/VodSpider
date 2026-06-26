package com.github.catvod.js.bean;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class Cache {

    private String pdfhHtml;
    private String pdfaHtml;
    private Document pdfhDoc;
    private Document pdfaDoc;
    private long pdfhTime;
    private long pdfaTime;
    private boolean pdfhValid;
    private boolean pdfaValid;

    public Cache() {
        pdfhValid = false;
        pdfaValid = false;
    }

    public Document getPdfh(String html) {
        updatePdfh(html);
        return pdfhDoc;
    }

    public Document getPdfa(String html) {
        updatePdfa(html);
        return pdfaDoc;
    }

    public boolean isPdfhValid() {
        return pdfhValid;
    }

    public boolean isPdfaValid() {
        return pdfaValid;
    }

    public long getPdfhTime() {
        return pdfhTime;
    }

    public long getPdfaTime() {
        return pdfaTime;
    }

    public void clear() {
        pdfhHtml = null;
        pdfaHtml = null;
        pdfhDoc = null;
        pdfaDoc = null;
        pdfhTime = 0;
        pdfaTime = 0;
        pdfhValid = false;
        pdfaValid = false;
    }

    public void clearPdfh() {
        pdfhHtml = null;
        pdfhDoc = null;
        pdfhTime = 0;
        pdfhValid = false;
    }

    public void clearPdfa() {
        pdfaHtml = null;
        pdfaDoc = null;
        pdfaTime = 0;
        pdfaValid = false;
    }

    public boolean hasPdfh() {
        return pdfhHtml != null && !pdfhHtml.isEmpty();
    }

    public boolean hasPdfa() {
        return pdfaHtml != null && !pdfaHtml.isEmpty();
    }

    public boolean hasAny() {
        return hasPdfh() || hasPdfa();
    }

    public String getPdfhHtml() {
        return pdfhHtml;
    }

    public String getPdfaHtml() {
        return pdfaHtml;
    }

    public Document getPdfhDoc() {
        return pdfhDoc;
    }

    public Document getPdfaDoc() {
        return pdfaDoc;
    }

    public void setPdfhDoc(Document doc) {
        this.pdfhDoc = doc;
        this.pdfhValid = doc != null;
    }

    public void setPdfaDoc(Document doc) {
        this.pdfaDoc = doc;
        this.pdfaValid = doc != null;
    }

    public int size() {
        int size = 0;
        if (hasPdfh()) size++;
        if (hasPdfa()) size++;
        return size;
    }

    private void updatePdfh(String html) {
        if (html == null || html.isEmpty()) {
            pdfhValid = false;
            return;
        }
        if (html.equals(pdfhHtml)) return;
        long startTime = System.currentTimeMillis();
        pdfhDoc = Jsoup.parse(pdfhHtml = html);
        pdfhTime = System.currentTimeMillis() - startTime;
        pdfhValid = pdfhDoc != null;
    }

    private void updatePdfa(String html) {
        if (html == null || html.isEmpty()) {
            pdfaValid = false;
            return;
        }
        if (html.equals(pdfaHtml)) return;
        long startTime = System.currentTimeMillis();
        pdfaDoc = Jsoup.parse(pdfaHtml = html);
        pdfaTime = System.currentTimeMillis() - startTime;
        pdfaValid = pdfaDoc != null;
    }
}