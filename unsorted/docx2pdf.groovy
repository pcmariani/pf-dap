// https://mvnrepository.com/artifact/org.apache.poi/poi-ooxml
@Grapes(
    @Grab(group='org.apache.poi', module='poi-ooxml', version='5.2.3')
)
// https://mvnrepository.com/artifact/fr.opensagres/org.apache.poi.xwpf.converter
@Grapes(
    @Grab(group='fr.opensagres', module='org.apache.poi.xwpf.converter', version='0.9.1')
)

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.poi.xwpf.converter.pdf.PdfConverter;
import org.apache.poi.xwpf.converter.pdf.PdfOptions;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

println "hello world"
