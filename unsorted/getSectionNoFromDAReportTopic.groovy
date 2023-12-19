import com.boomi.execution.ExecutionUtil;

for ( int i = 0; i < dataContext.getDataCount(); i++ ) {

InputStream is = dataContext.getStream(i);
Properties props = dataContext.getProperties(i);

    def root = new XmlParser().parse(is)
    // println root.'**'.section_no.text()

    ExecutionUtil.setDynamicProcessProperty("DPP_SectionNumber", root.'**'.section_no.text(), false);

    def outData = groovy.xml.XmlUtil.serialize(root).replaceAll("\\<\\?xml(.+?)\\?\\>", "").trim()
    is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}
