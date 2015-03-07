package puslogger.pi.logger;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

//This custom formatter formats parts of a log record to a single line
public class HTMLFormatter extends Formatter {
	private static final int INFO = 0x320;
	private static final int WARNING = 0x384;
	private static final int SEVERE = 0x3E8;

	// This method is called for every log records
	public String format(LogRecord rec) {
		StringBuffer buf = new StringBuffer(1000);
		// Bold any levels >= WARNING
		buf.append("<tr>");
		buf.append("<td>");
		switch (rec.getLevel().intValue()) {
		case INFO:
			buf.append("<p style=\"color:#0B5959\">");
			buf.append("<b>");
			buf.append(rec.getLevel());
			buf.append("</b>");
			buf.append("</p>");
			break;
		case WARNING:
			buf.append("<p style=\"color:#EE6E1A\">");
			buf.append("<b>");
			buf.append(rec.getLevel());
			buf.append("</b>");
			buf.append("</p>");
			break;
		case SEVERE:
			buf.append("<p style=\"color:#AA0000\">");
			buf.append("<b>");
			buf.append(rec.getLevel());
			buf.append("</b>");
			buf.append("</p>");
			break;
		default:
			buf.append(rec.getLevel());
			break;
		}
		buf.append("</td>");
		buf.append("<td>");
		buf.append(this.calcDate(rec.getMillis()));
		buf.append("</td>");
		buf.append("<td>");
		buf.append(this.formatMessage(rec));
		buf.append("</td>");
		Throwable t = rec.getThrown();
		if (t != null) {
			buf.append("<td>");
			buf.append(this.printStackTrace(t));
			buf.append("</td>");
		}
		buf.append("</tr>\n");
		return buf.toString().replace(System.getProperty("line.separator"),
				"<br/>\n");
	}

	private String printStackTrace(Throwable t) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		t.printStackTrace(pw);
		String stackTrace = sw.toString();
		pw.close();
		return stackTrace;
	}

	private String calcDate(long millisecs) {
		SimpleDateFormat date_format = new SimpleDateFormat("MMM dd,yyyy HH:mm");
		Date resultdate = new Date(millisecs);
		return date_format.format(resultdate);
	}

	// This method is called just after the handler using this
	// formatter is created
	public String getHead(Handler h) {
		return "<HTML>\n<HEAD>\n"
				+ (new Date())
				+ "\n<link media=\"screen\" rel=\"stylesheet\" href=\"colorbox.css\" />"
				+ "<script src=\"https://ajax.googleapis.com/ajax/libs/jquery/1.6.1/jquery.min.js\"></script>"
				+ "<script src=\"jquery.colorbox.js\"></script>"
				+ "</HEAD>\n<BODY>\n<PRE>\n"
				+ "<table width=\"100%\" border>\n " + "<tr><th>Level</th>"
				+ "<th>Time</th>" + "<th>Log Message</th>"
				+ "<th>Throwable</th>" + "</tr>\n";
	}

	// This method is called just after the handler using this
	// formatter is closed
	public String getTail(Handler h) {
		return "</table>\n </PRE></BODY>\n</HTML>\n";
	}
}