import java.util.Properties;
import java.io.InputStream;

def NEWLINE = System.lineSeparator()

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def reader = new BufferedReader(new InputStreamReader(is))
    def outData = new StringBuilder()

    int numDigits = 2
    int n = 0

    while ((line = reader.readLine()) != null ) {
        String num = "0"*(numDigits-n.toString().size()) + n
        outData.append("LINE$num" + line + NEWLINE)
        n++
    }

    is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}
