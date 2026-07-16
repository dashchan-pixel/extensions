package chan.text;

import chan.library.api.BuildConfig;
import java.io.IOException;
import java.io.Reader;

/**
 * <p>HTML text parser. Can work in two modes: linear and group.</p>
 *
 * <p>In linear mode, parser will call {@link Callback#onStartElement(GroupParser, String, Attributes)}  every
 * time parser reaches new tag. This method has boolean result, and when this method returns true - parser switches
 * to group mode.</p>
 *
 * <p>In group mode parser will handle all text inside started tag. Then it call
 * {@link Callback#onGroupComplete(GroupParser, String)} with all text inside tag.</p>
 */
public final class GroupParser {
	/**
	 * <p>Attributes holder and parser.</p>
	 */
	public static final class Attributes {
		private Attributes() {
			BuildConfig.Private.expr();
		}

		/**
		 * <p>Parses the attribute and returns its value if attribute exists.</p>
		 *
		 * @param attribute Attribute name.
		 * @return Attribute value.
		 */
		@Override
		public String toString() {
			try {
				java.lang.reflect.Field field = getClass().getDeclaredField("html");
				field.setAccessible(true);
				CharSequence seq = (CharSequence) field.get(this);
				return seq != null ? seq.toString() : "";
			} catch (Exception e) {
				return "";
			}
		}

		public String get(String attribute) {
			return BuildConfig.Private.expr(attribute);
		}

		/**
		 * <p>Checks the attributes line contains the string.</p>
		 *
		 * @param string String to search for.
		 * @return True if string contains the {@code string}.
		 */
		public boolean contains(CharSequence string) {
			return BuildConfig.Private.expr(string);
		}
	}

	/**
	 * <p>Callback for {@link GroupParser}.</p>
	 */
	public interface Callback {
		default boolean onStartElement(GroupParser parser, String tagName, Attributes attributes)
				throws ParseException {
			return onStartElement(parser, tagName, attributes.toString());
		}

		default boolean onStartElement(GroupParser parser, String tagName, String attrs)
				throws ParseException {
			return false;
		}

		void onEndElement(GroupParser parser, String tagName) throws ParseException;

		default void onText(GroupParser parser, CharSequence text) throws ParseException {
			onText(parser, text.toString(), 0, text.length());
		}

		default void onText(GroupParser parser, String source, int start, int end) throws ParseException {}

		void onGroupComplete(GroupParser parser, String text) throws ParseException;
	}

	public static String extractAttr(CharSequence html, String attribute) {
		return BuildConfig.Private.expr(html, attribute);
	}

	private GroupParser() {
		BuildConfig.Private.expr();
	}

	/**
	 * <p>Starts a new parsing process.</p>
	 *
	 * @param source String to parse.
	 * @param callback Callback to handle parsed data.
	 * @throws ParseException when parsing process was interrupted.
	 */
	public static void parse(String source, Callback callback) throws ParseException {
		BuildConfig.Private.<ParseException>error();
		BuildConfig.Private.expr(source, callback);
	}

	/**
	 * <p>Starts a new parsing process.</p>
	 *
	 * @param reader Input to parse.
	 * @param callback Callback to handle parsed data.
	 * @throws IOException when reading process was interrupted due to I/O problem.
	 * @throws ParseException when parsing process was interrupted.
	 */
	public static void parse(Reader reader, Callback callback) throws IOException, ParseException {
		BuildConfig.Private.<IOException>error();
		BuildConfig.Private.<ParseException>error();
		BuildConfig.Private.expr(reader, callback);
	}
}
