package puslogger.pi;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import puslogger.pi.logger.HTMLFormatter;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.Pin;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;

public class PUSLogger {
	static {
		try {
			if (!new File("./log-config.properties").exists())
				throw new FileNotFoundException(String.format(
						"Configuration file '%s' was not found.",
						"log-config.properties"));
			System.setProperty("java.util.logging.config.file",
					"./log-config.properties");
			Field fieldSysPath;
			fieldSysPath = ClassLoader.class.getDeclaredField("sys_paths");
			fieldSysPath.setAccessible(true);
			fieldSysPath.set(null, null);
		} catch (Exception e) {
			System.err.printf("SEVERE EXCEPTION! (%s)%n",
					e.getLocalizedMessage());
			e.printStackTrace();
			Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
			logger.setLevel(Level.INFO);
			ConsoleHandler console = new ConsoleHandler();
			console.setFormatter(new SimpleFormatter());
			logger.addHandler(console);
			try {
				FileHandler handler = new FileHandler("/srv/http/index.html");
				Formatter formatter = new HTMLFormatter();
				handler.setFormatter(formatter);
				logger.addHandler(handler);
			} catch (Exception ex) {
				System.err.printf("SEVERE EXCEPTION! (%s)%n",
						e.getLocalizedMessage());
				ex.printStackTrace();
			}
		}
	}
	private static final Log LOGGER = LogFactory.getLog(PUSLogger.class);
	private static final String USER_NAME = "root";
	private static final String PASSWORD = "raspian";
	private static final String LOCATION = "192.168.30.132:3306";
	private static final String URL = "jdbc:mysql://" + LOCATION + "/puslogger";
	private static final HashMap<Pin, String> STATIC_NAMES = new HashMap<Pin, String>();
	static {
		STATIC_NAMES.put(RaspiPin.GPIO_07, "NETTO - [FALS 1]");
		STATIC_NAMES.put(RaspiPin.GPIO_00, "TILL STACKER - [FALS 1]");
	}
	private static Connection conn;
	private static Statement stat;
	private static LinkedList<ValueWrapper> values = new LinkedList<ValueWrapper>();
	private static LinkedList<ValueWrapper> buffer = new LinkedList<ValueWrapper>();
	// Just testing a few stuff...
	private static boolean reading;

	public static void main(String[] args) {
		// create GPIO controller
		GpioController gpio = GpioFactory.getInstance();
		GpioPinDigitalInput[] pins = {
				gpio.provisionDigitalInputPin(RaspiPin.GPIO_07,
						PinPullResistance.PULL_DOWN),
				gpio.provisionDigitalInputPin(RaspiPin.GPIO_00,
						PinPullResistance.PULL_DOWN) };
		Date date = trim(new Date());
		Calendar c = GregorianCalendar.getInstance();
		c.setTime(date);
		int minute = c.get(Calendar.MINUTE);
		minute = minute % 5;
		if (minute != 0) {
			int minuteToAdd = 5 - minute;
			c.add(Calendar.MINUTE, minuteToAdd);
		}
		Timer tim = new Timer();
		int delay;
		tim.schedule(
				new TimerTask() {
					@Override
					public void run() {
						Wait a = new Wait();
						synchronized (a) {
							a.start();
							try {
								a.wait();
							} catch (InterruptedException e) {
								LOGGER.error("Unexpected exception.", e);
								e.printStackTrace();
							}
						}
						int badAttempts = 0;
						boolean flag = false;
						while (badAttempts < 4) {
							try {
								if (values.size() > 0 || buffer.size() > 0) {
									Class.forName("com.mysql.jdbc.Driver")
											.newInstance();
									conn = DriverManager.getConnection(URL,
											USER_NAME, PASSWORD);
									stat = conn.createStatement();
									if (values.size() > 0) {
										String table = new SimpleDateFormat(
												"yyyy-MM-dd_HH.mm")
												.format(new Date());
										stat.execute(String
												.format("DROP TABLE IF EXISTS `puslogger`.`%s`;",
														table));
										stat.execute(String
												.format("CREATE TABLE `puslogger`.`%s` ("
														+ " `name` text COLLATE utf8_swedish_ci NOT NULL"
														+ ",`value` int(11) NOT NULL"
														+ ",`date` bigint(20) NOT NULL"
														+ ") ENGINE=MyISAM DEFAULT CHARSET=utf8 COLLATE=utf8_swedish_ci;",
														table));
										for (ValueWrapper wrapper : values) {
											stat.execute(String
													.format("INSERT INTO `puslogger`.`%s` ("
															+ "`name`, `value`, `date`"
															+ ") VALUES ("
															+ "'%s', '%d', '%d'"
															+ ");", table,
															wrapper.getName(),
															wrapper.getValue(),
															wrapper.getDate()
																	.getTime()));
										}
										LOGGER.info("Successfully established connection to database and inserted values.");
									}
									int attemptsBad = 0;
									boolean galf = false;
									while (attemptsBad < 4) {
										try {
											if (buffer.size() > 0) {
												for (ValueWrapper wrapper : buffer) {
													String table = new SimpleDateFormat(
															"yyyy-MM-dd_HH.mm")
															.format(wrapper
																	.getDate());
													stat.execute(String
															.format("CREATE TABLE IF NOT EXISTS `puslogger`.`%s` ("
																	+ " `name` text COLLATE utf8_swedish_ci NOT NULL"
																	+ ",`value` int(11) NOT NULL"
																	+ ",`date` bigint(20) NOT NULL"
																	+ ") ENGINE=MyISAM DEFAULT CHARSET=utf8 COLLATE=utf8_swedish_ci;",
																	table));
												}
												for (ValueWrapper wrapper : buffer) {
													stat.execute(String
															.format("INSERT INTO `puslogger`.`%s` ("
																	+ "`name`, `value`, `date`"
																	+ ") VALUES ("
																	+ "'%s', '%d', '%d'"
																	+ ");",
																	new SimpleDateFormat(
																			"yyyy-MM-dd_HH.mm")
																			.format(wrapper
																					.getDate()),
																	wrapper.getName(),
																	wrapper.getValue(),
																	wrapper.getDate()
																			.getTime()));
												}
												LOGGER.info("Successfully established connection to database and inserted buffered values.");
											}
											galf = false;
											break;
										} catch (Exception e) {
											e.printStackTrace();
											attemptsBad++;
											galf = true;
											LOGGER.warn(
													String.format(
															"Exception while attempting to instantiate connection to database at %s.",
															LOCATION), e);
										} finally {
											if (!galf)
												while (buffer.size() > 0)
													buffer.pollFirst();
										}
									}
								}
								flag = false;
								break;
							} catch (Exception e) {
								e.printStackTrace();
								badAttempts++;
								flag = true;
								LOGGER.warn(
										String.format(
												"Exception while attempting to instantiate connection to database at %s.",
												LOCATION), e);
							} finally {
								try {
									if (conn != null && conn.isClosed() != true)
										conn.close();
									if (stat != null && stat.isClosed() != true)
										stat.close();
								} catch (SQLException e) {
									e.printStackTrace();
								}
								if (!flag)
									while (values.size() > 0)
										values.pollFirst();
							}
						}
						if (badAttempts >= 4) {
							LOGGER.error(String
									.format("Failed to connect to database at %s, %d times (couldn't insert values).",
											LOCATION, badAttempts));
							buffer.addAll(values);
							StringWriter sw = new StringWriter();
							PrintWriter pw = new PrintWriter(sw);
							while (values.size() > 0) {
								ValueWrapper wrapper = values.getFirst();
								pw.printf("NAME: %s TIME: %d VALUE: %d<br />",
										wrapper.getName(), wrapper.getDate()
												.getTime(), wrapper.getValue());
								values.pollFirst();
							}
							LOGGER.info(String
									.format("<button onclick=\"var height = $(window).height();var x = ((height - ((80/100)*height)) / 2);$.colorbox({html:'<b>%s</b>',width:'80%%', height:'80%%',top:''+x+'',fixed:true,transition:'elastic',speed:500});\">Show values</button>",
											sw.toString()));
							pw.close();
						}
					}
				},
				(delay = (int) (c.getTimeInMillis() - new Date().getTime())) > 1 ? delay
						: 0, 300000);
		// create GPIO listener
		GpioPinListenerDigital listener = new GpioPinListenerDigital() {
			@Override
			public void handleGpioPinDigitalStateChangeEvent(
					GpioPinDigitalStateChangeEvent event) {
				if (reading) {
					values.add(new ValueWrapper(STATIC_NAMES.get(event.getPin()
							.getPin()), event.getState().getValue(), new Date()));
				}
			}
		};
		// create and register gpio pin listener
		gpio.addListener(listener, pins);
	}

	private static Date trim(Date date) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.set(Calendar.MILLISECOND, 0);
		calendar.set(Calendar.SECOND, 0);
		return new Date(calendar.getTimeInMillis() + (1000 * 60));
	}

	private static class Wait extends Thread {
		@Override
		public void run() {
			synchronized (this) {
				reading = true;
				LockSupport.parkNanos(10000000000L);
				this.notifyAll();
			}
			reading = false;
		}
	}

	private static class ValueWrapper {
		private String name;
		private int value;
		private Date date;

		public ValueWrapper(String name, int value, Date date) {
			this.name = name;
			this.value = value;
			this.date = date;
		}

		public String getName() {
			return name;
		}

		public int getValue() {
			return value;
		}

		public Date getDate() {
			return date;
		}
	}
}