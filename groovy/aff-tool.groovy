#!/usr/bin/env groovy

@GrabResolver(name="jcenter", root="http://jcenter.bintray.com", m2Compatible="true")

@Grapes([
	@Grab("org.slf4j:slf4j-api:1.7.25"),
	//@Grab("org.slf4j:slf4j-simple:1.7.25"),
	//@Grab(group="org.slf4j", module="slf4j-simple", version="1.7.25", scope="test"),
	@Grab("ch.qos.logback:logback-classic:1.2.3"),
	@Grab(group="org.jsoup", module="jsoup", version="1.11.3"),
	@Grab(group='com.beust', module='jcommander', version='1.72'),
	@Grab('info.picocli:picocli:3.0.0-beta-2'),
	@Grab(group='com.squareup.okhttp3', module='okhttp', version='3.10.0'),
	@Grab(group='org.ehcache', module='ehcache', version='3.5.2'),
])

//import org.apache.commons.cli.*
//import com.beust.jcommander.*
import org.slf4j.*
import groovy.util.logging.*
import ch.qos.logback.classic.*
import groovy.transform.*
import groovy.transform.builder.*
import picocli.*
import static picocli.CommandLine.*
import okhttp3.*;
import org.jsoup.*;
import org.jsoup.nodes.*;
import org.jsoup.select.*;
import org.jsoup.helper.*;
import java.nio.file.*;
import java.util.concurrent.*;
//import org.ehcache.config.builders.*;
//import org.ehcache.config.*;
//import org.ehcache.*;

// http://members.adult-fanfiction.org/profile.php?no=1296867190
// http://members.adult-fanfiction.org/profile.php?no=1296867190&view=story
// http://members.adult-fanfiction.org/profile.php?no=1296867190&view=story&zone=inu
// http://members.adult-fanfiction.org/profile.php?no=1296867190&view=story&zone=inu&page=2
// http://inu.adult-fanfiction.org/story.php?no=600097437
// http://inu.adult-fanfiction.org/story.php?no=600097437&chapter=2

@Slf4j
class UrlHelper {

	static class ResponseCacheInterceptor implements Interceptor {
		@Override
		public okhttp3.Response intercept(Chain chain) throws IOException {
			okhttp3.Response originalResponse = chain.proceed(chain.request());
			return originalResponse.newBuilder()
				.header("Expires", java.time.ZonedDateTime.now().plusDays(1).format(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME))
				.header("Cache-Control", "public, max-age=${24 * 60 * 60}, max-stale=${24 * 60 * 60}")
				.build()
		}
	}

	/*
	private static class OfflineResponseCacheInterceptor implements Interceptor {
		@Override
		public okhttp3.Response intercept(Chain chain) throws IOException {
			Request request = chain.request();
			//if (!UtilityMethods.isNetworkAvailable()) {
				request = request.newBuilder()
						.header("Expires", java.time.ZonedDateTime.now().format(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME))
						.header("Cache-Control", "public, only-if-cached, max-stale=${24 * 60 * 60}")
						.build();
			//}
			return chain.proceed(request);
		}
	}
	*/

	OkHttpClient client;
	CacheControl cacheControl;
	Cache cache;

	UrlHelper() {
		this.cache = new Cache(FileSystems.getDefault().getPath(System.getProperty("user.home"), ".cache", "aff-tool").toFile(), 1*1024*1024*1024)

		this.cacheControl = new CacheControl.Builder()
			//.maxAge(1, TimeUnit.DAYS)
			//.maxStale(1, TimeUnit.DAYS)
			//.minFresh(1, TimeUnit.DAYS)
			//.onlyIfCached()
			.build()

		OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
			.addNetworkInterceptor(new ResponseCacheInterceptor())
			//.addInterceptor(new ResponseCacheInterceptor())
			//.addInterceptor(new OfflineResponseCacheInterceptor())
			//.cache(cache);
		clientBuilder.setInternalCache(this.cache.internalCache);
		this.client = clientBuilder.build();
	}

	String getUrl( String url ) {
		Request request = new Request.Builder()
			.url(url)
			.cacheControl(cacheControl)
			.build();
		Response response = null;
		response = this.cache.get(request)
		if (response == null) {
			response = client.newCall(request).execute()
		}
		log.debug("response = ${response}, reponse.headers() = ${response.headers()}")
		if ( response.code() != 200 ) throw new RuntimeException("Failed to get ${url} - ${response}")
		return response.body().string()
	}
}

class FormattingVisitor implements NodeVisitor {
	Integer maxWidth = null;
	private int width = 0;
	private StringBuilder accum = new StringBuilder(); // holds the accumulated text

	// hit when the node is first seen
	public void head(Node node, int depth) {
		String name = node.nodeName();
		if (node instanceof TextNode)
			append(((TextNode) node).text()); // TextNodes carry all user-readable text in the DOM.
		else if (name.equals("li"))
			append("\n * ");
		else if (name.equals("dt"))
			append("  ");
		else if (StringUtil.in(name, "p", "h1", "h2", "h3", "h4", "h5", "tr"))
			append("\n");
	}

	// hit when all of the node's children (if any) have been visited
	public void tail(Node node, int depth) {
		String name = node.nodeName();
		if (StringUtil.in(name, "br", "dd", "dt", "p", "h1", "h2", "h3", "h4", "h5"))
			append("\n");
		else if (name.equals("a"))
			append(String.format(" <%s>", node.absUrl("href")));
	}

	// appends text to the string builder with a simple word wrap method
	private void append(String text) {
		if (text.startsWith("\n"))
			width = 0; // reset counter if starts with a newline. only from formats above, not in natural text
		if (text.equals(" ") &&
				(accum.length() == 0 || StringUtil.in(accum.substring(accum.length() - 1), " ", "\n")))
			return; // don't accumulate long runs of empty spaces

		if ( maxWidth != null && (text.length() + width > maxWidth) ) { // won't fit, needs to wrap
			String words[] = text.split("\\s+");
			for (int i = 0; i < words.length; i++) {
				String word = words[i];
				boolean last = i == words.length - 1;
				if (!last) // insert a space if not the last word
					word = word + " ";
				if ( maxWidth != null && (word.length() + width > maxWidth)) { // wrap and reset counter
					accum.append("\n").append(word);
					width = word.length();
				} else {
					accum.append(word);
					width += word.length();
				}
			}
		} else { // fits as is, without need to wrap text
			accum.append(text);
			width += text.length();
		}
	}

	@Override
	public String toString() {
		return accum.toString();
	}
}

class Context {
	static final UrlHelper urlHelper = new UrlHelper();
    static String getPlainText(Element document, Integer maxWidth) {
        FormattingVisitor formatter = new FormattingVisitor(maxWidth:maxWidth);
        NodeTraversor.traverse(formatter, document); // walk the DOM, and call .head() and .tail() for each node
        return formatter.toString().replaceAll("\n\n+","\n\n")
    }
}

@Slf4j
@ToString(includeNames=true,includeFields=true)
class AffResource {
	String rawUrl
	HttpUrl rawHttpUrl
	static AffResource fromUrl(String url, params = [:]) {
		log.info("url = ${url}")
		HttpUrl httpUrl = HttpUrl.parse(url);
		log.info("httpUrl = ${httpUrl}")
		//def params = [:]
		params["rawUrl"] = url
		params["rawHttpUrl"] = httpUrl
		log.info("httpUrl = ${httpUrl}")
		if ( httpUrl.host == "members.adult-fanfiction.org" && httpUrl.encodedPath() == "/profile.php" ) {
			params["memberId"] = httpUrl.queryParameter("no") as int
			if ( httpUrl.queryParameter("view") == "story" ) {
				if ( httpUrl.queryParameter("zone") != null ) {
					params["zone"] = httpUrl.queryParameter("zone")
					if ( httpUrl.queryParameter("page") ) {
						params["page"] = httpUrl.queryParameter("page") as int
						return params as AffMemberStoriesZonePage
					}
					return params as AffMemberStoriesZone
				}
				return params as AffMemberStories
			}
			return params as AffMember
		}
		else if ( httpUrl.encodedPath() == "/story.php" ) {
			params["storyId"] = httpUrl.queryParameter("no") as int
			params["zone"] = httpUrl.host.replace(".adult-fanfiction.org", "")
			if ( httpUrl.queryParameter("chapter") ) {
				params["chapter"] = httpUrl.queryParameter("chapter") as int
				return params as AffStoryChapter
			}
			return params as AffStory
		}
		throw RuntimeException("Failed to process url ${url}")
	}
	void fetch() {
	}
}

@Slf4j
@ToString(includeNames=true,includeFields=true,includeSuper=true)
class AffMember extends AffResource {
	int memberId
	HttpUrl.Builder httpUrlBuilder() {
		log.debug("this.memberId = ${this.memberId}")
		return new HttpUrl.Builder()
			.scheme("http")
			.host("members.adult-fanfiction.org")
			.addPathSegment("profile.php")
			.addQueryParameter("no", this.memberId as String)
	}
	HttpUrl getHttpUrl() {
		return this.httpUrlBuilder().build()
	}
	String getUrl() {
		return this.httpUrl.toString()
	}
	AffMemberStories getStories() {
		return new AffMemberStories(memberId:memberId)
	}
	void fetch() {
		this.stories.fetch()
	}
}

@Slf4j
@ToString(includeNames=true,includeFields=true,includeSuper=true)
class AffMemberStories extends AffMember {
	private List<AffMemberStoriesZone> _zones = null

	HttpUrl.Builder httpUrlBuilder() {
		return super.httpUrlBuilder()
			.addQueryParameter("view", "story")
	}
	HttpUrl getHttpUrl() {
		return this.httpUrlBuilder().build()
	}
	String getUrl() {
		return this.httpUrl.toString()
	}
	List<AffMemberStoriesZone> zones() {
		if ( this._zones == null ) {
			this._zones = new ArrayList<AffMemberStoriesZone>();
			def docString = Context.urlHelper.getUrl(this.url)
			//log.debug("docString = ${docString}")
			Document document = Jsoup.parse(docString, this.url)
			//log.debug("doc = ${doc}")
			Elements elements = document.select("#contentdata").first().select("a[href]")
			//log.debug("elements = ${elements}")
			for ( element in elements ) {
				log.debug("element = ${element}")
				def href = element.attr("href");
				log.debug("href = ${href}")
				_zones.add(AffResource.fromUrl(href))
			}
		}
		return this._zones;
	}
	void fetch() {
		//log.debug("zones = ${this.zones(urlHelper)}")
		for ( zone in this.zones() ) {
			log.debug("zone = ${zone}")
			zone.fetch()
		}
	}
}

@Slf4j
@ToString(includeNames=true,includeFields=true,includeSuper=true)
class AffMemberStoriesZone extends AffMemberStories {
	private List<AffMemberStoriesZonePage> _pages = null
	String zone
	HttpUrl.Builder httpUrlBuilder() {
		return super.httpUrlBuilder()
			.addQueryParameter("zone", this.zone)
	}
	HttpUrl getHttpUrl() {
		return this.httpUrlBuilder().build()
	}
	String getUrl() {
		return this.httpUrl.toString()
	}
	List<AffMemberStoriesZonePage> pages() {
		if ( this._pages == null ) {
			this._pages = new ArrayList<AffMemberStoriesZonePage>();
			def docString = Context.urlHelper.getUrl(this.url)
			//log.debug("docString = ${docString}")
			Document document = Jsoup.parse(docString, this.url)
			//log.debug("document = ${document}")
			Elements elements = document.select("#contentdata > .pagination > ul > li > a[href]")
			//log.debug("elements = ${elements}")
			int pageCount = 1;
			AffMemberStoriesZonePage firstPage = new AffMemberStoriesZonePage(memberId: this.memberId, zone: this.zone, page: 1)
			AffMemberStoriesZonePage lastPage = null;
			for ( element in elements ) {
				log.debug("element = ${element}, element.text() = ${element.text()}")
				if ( element.text() == ">>" ) {
					log.debug("got end ptr ${element}")
					lastPage = AffResource.fromUrl(element.attr("href"))
				}
			}
			log.debug("lastPage = ${lastPage}")
			this._pages.add(firstPage)
			if ( lastPage != null ) {
				for ( int i = 2; i < lastPage.page; ++i ) {
					this._pages.add( new AffMemberStoriesZonePage(memberId: this.memberId, zone: this.zone, page: i) )
				}
				this._pages.add(lastPage)
			}
		}
		return this._pages;
	}
	void fetch() {
		//log.debug("zones = ${this.zones(urlHelper)}")
		for ( page in this.pages() ) {
			log.debug("page = ${page}")
			page.fetch()
		}
	}
}

@Slf4j
@ToString(includeNames=true,includeFields=true,includeSuper=true)
class AffMemberStoriesZonePage extends AffMemberStoriesZone {
	private List<AffStory> _stories = null
	int page
	HttpUrl.Builder httpUrlBuilder() {
		def builder = super.httpUrlBuilder();
		if (page > 1)
			builder.addQueryParameter("page", this.page as String)
		return builder;
	}
	HttpUrl getHttpUrl() {
		return this.httpUrlBuilder().build()
	}
	String getUrl() {
		return this.httpUrl.toString()
	}
	List<AffStory> stories() {
		if ( this._stories == null ) {
			this._stories = new ArrayList<AffStory>();
			def docString = Context.urlHelper.getUrl(this.url)
			//log.debug("docString = ${docString}")
			Document document = Jsoup.parse(docString, this.url)
			//log.debug("document = ${document}")
			Elements elements = document.select("#contentdata > .alist > ul > li a[href*=adult-fanfiction.org/story.php]")
			//log.debug("elements = ${elements}")
			for ( element in elements ) {
				log.debug("element = ${element}}")
				def href = element.attr("href")
				log.debug("href = ${href}")
				this._stories.add(AffResource.fromUrl(href))
			}
		}
		return this._stories;
	}
	void fetch() {
		for ( story in this.stories() ) {
			log.debug("story = ${story}")
			story.fetch()
		}
	}
}

@Slf4j
@ToString(includeNames=true,includeFields=true,includeSuper=true)
class AffStory extends AffResource {
	private List<AffStoryChapter> _chapters = null
	String zone
	int storyId
	HttpUrl.Builder httpUrlBuilder() {
		return new HttpUrl.Builder()
			.scheme("http")
			.host("${this.zone}.adult-fanfiction.org")
			.addPathSegment("story.php")
			.addQueryParameter("no", this.storyId as String)
	}
	HttpUrl getHttpUrl() {
		return this.httpUrlBuilder().build()
	}
	String getUrl() {
		return this.httpUrl.toString()
	}
	List<AffStoryChapter> chapters() {
		if ( this._chapters == null ) {
			this._chapters = new ArrayList<AffStoryChapter>();
			def docString = Context.urlHelper.getUrl(this.url)
			//log.debug("docString = ${docString}")
			Document document = Jsoup.parse(docString, this.url)
			//log.debug("document = ${document}")
			//Elements elements = document.select("#contentdata > .pagination > ul > li > a[href]")
			Elements elements = document.select("#contentdata .pagination > ul > li > a[href]")
			log.debug("elements = ${elements}")
			int chapterCount = 1;
			AffStoryChapter firstChapter = new AffStoryChapter(zone: this.zone, storyId: this.storyId, chapter: 1)
			AffStoryChapter lastChapter = null;
			for ( element in elements ) {
				log.debug("element = ${element}, element.text() = ${element.text()}")
				if ( element.text() == ">>" ) {
					log.debug("got end ptr ${element}")
					lastChapter = AffResource.fromUrl("http://${this.zone}.adult-fanfiction.org${element.attr("href")}")
				}
			}
			log.debug("lastChapter = ${lastChapter}")
			this._chapters.add(firstChapter)
			if ( lastChapter != null ) {
				for ( int i = 2; i < lastChapter.chapter; ++i ) {
					this._chapters.add( new AffStoryChapter(zone: this.zone, storyId: this.storyId, chapter: i) )
				}
				this._chapters.add(lastChapter)
			}
		}
		return this._chapters;
	}
	void fetch() {
		//log.debug("zones = ${this.zones(urlHelper)}")
		for ( chapter in this.chapters() ) {
			log.debug("chapter = ${chapter}")
			chapter.fetch()
		}
	}
}

@Slf4j
@ToString(includeNames=true,includeFields=true,includeSuper=true, excludes="documentString,document")
class AffStoryChapter extends AffStory {
	int chapter

	
	private boolean _processed = false
	private def documentString;
	Document document;
	
	Integer memberId;
	String memberName;
	String storyName;
	String chapterName;

	HttpUrl.Builder httpUrlBuilder() {
		def builder = super.httpUrlBuilder();
		if (this.chapter > 1)
			builder.addQueryParameter("chapter", this.chapter as String)
		return builder;
	}
	HttpUrl getHttpUrl() {
		return this.httpUrlBuilder().build()
	}
	String getUrl() {
		return this.httpUrl.toString()
	}
	void process() {
		if ( this._processed ) return;
		this.documentString = Context.urlHelper.getUrl(this.url)
		this.document = Jsoup.parse(this.documentString, this.url)
		L:{
			Elements elements = document.select("#contentdata > table:first-of-type > tbody:first-of-type > tr:first-of-type > td:first-of-type")
			//log.debug("elements = ${elements}")
			Element nameElement = elements.select("a[href*=/story.php?no=]").first()
			log.debug("nameElement = ${nameElement}")
			this.storyName = nameElement.text()
			Element memberElement = elements.select("a[href*=http://members.adult-fanfiction.org/profile.php?no=]").first()
			log.debug("memberElement = ${memberElement}")
			this.memberName = memberElement.text()
			this.memberId = AffResource.fromUrl(memberElement.attr("href")).memberId
		}
		L:{
			Elements chapterElements = document.select("#contentdata > table:first-of-type > tbody:first-of-type > tr:nth-of-type(2) > td:first-of-type .dropdown > .dropdown-content > a[href\$=\"chapter=${this.chapter}\"]")
			log.debug("chapterElements = ${chapterElements}")
			this.chapterName = chapterElements.text().replaceFirst("^${this.chapter}-", "")
			/*
			Element nameElement = elements.select("a[href*=/story.php?no=]").first()
			log.debug("nameElement = ${nameElement}")
			this.storyName = nameElement.text()
			Element memberElement = elements.select("a[href*=http://members.adult-fanfiction.org/profile.php?no=]").first()
			log.debug("memberElement = ${memberElement}")
			this.memberName = memberElement.text()
			*/
		}
		this._processed = true;
	}
	Integer getMemberId() {
		if (this.memberId == null) this.process()
		return this.memberId
	}
	String getMemberName() {
		if (this.memberName == null) this.process()
		return this.memberName
	}
	String getStoryName() {
		if (this.storyName == null) this.process()
		return this.storyName
	}
	String getChapterName() {
		if (this.chapterName == null) this.process()
		return this.chapterName
	}
	Document getDocument() {
		if (this.document == null) this.process()
		return this.document
	}
	String getDocumentString() {
		if (this.documentString == null) this.process()
		return this.documentString
	}
	String fileName() {
		return "aff - ${this.memberName} _ ${this.memberId} - ${this.storyName} _ ${this.storyId} - chapter ${this.chapter} - ${this.chapterName}".replaceAll("[/]","_")
	}
	void fetch() {
		this.process()
		String fileName = this.fileName()
		log.debug("fileName = ${fileName}")
		new File("${fileName}.html").withWriter() { writer ->
			writer.print(this.documentString)
		}
		new File("${fileName}.txt").withWriter() { writer ->
			org.jsoup.nodes.Element element = this.document;
			Integer wrap = null;
			//writer.print(Context.getPlainText((org.jsoup.nodes.Element)this.document, (Integer)null))
			writer.print(Context.getPlainText(element.select("#contentdata").first(), wrap))
		}
	}
}

@Slf4j
@ToString(includeNames=true,includeFields=true)
class Args {
	@Option(names = [ "-v", "--verbose" ], description = "Be verbose.")
	boolean[] verbosity;

	@Parameters(index = "0..*", arity="1..*")
	List<String> urls
}

/*
@Slf4j
class UrlCache {
	private CacheManager cacheManager;
	private Cache<String, Object> urlCache;
	public UrlCache() {
		this.cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build();
		this.cacheManager.init();
		ResourcePools resourcePools = ResourcePoolsBuilder.newResourcePoolsBuilder().
			disk(1, MemoryUnit.GB, true)
			.build()
		//CacheConfigurationBuilder.
		//cacheManager.createCache("aff-tool-urlCache").
	}
}
*/

final Logger log = LoggerFactory.getLogger(Script.class);

log.debug "test debug ..."
def root = org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
root.setLevel(Level.ALL);
log.debug "test debug ..."
log.debug "this.args = ${this.args}"

def parsedArgs = new Args();
new CommandLine(parsedArgs).parse(this.args)
log.debug "args = ${args}"

//UrlCache urlCache = new UrlCache()
//UrlHelper urlHelper = new UrlHelper();

for ( url in parsedArgs.urls ) {
	def resource = AffResource.fromUrl(url)
	log.debug("resource = ${resource}")
	//log.debug("resource.memberId = ${resource.memberId}")
	log.debug("resource.url = ${resource.url}")
	resource.fetch()
}


// http://members.adult-fanfiction.org/profile.php?no=1296962854
// http://members.adult-fanfiction.org/profile.php?no=1296962854&view=story
// http://members.adult-fanfiction.org/profile.php?no=1296962854&view=story&zone=anime
// http://anime.adult-fanfiction.org/story.php?no=600055803
// http://anime.adult-fanfiction.org/story.php?no=600055803&chapter=2
// http://anime.adult-fanfiction.org/story.php?no=600055792&chapter=4

