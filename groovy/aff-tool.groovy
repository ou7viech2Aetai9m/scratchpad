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
])

//import org.apache.commons.cli.*
//import com.beust.jcommander.*
import org.slf4j.*
import groovy.util.logging.*
import ch.qos.logback.classic.*
import groovy.transform.*
import picocli.*
import static picocli.CommandLine.*

@ToString(includeNames=true,includeFields=true)
class Args {
	@Option(names = [ "-v", "--verbose" ], description = "Be verbose.")
	boolean[] verbosity;

	@Parameters(index = "0..*", arity="1..*")
	List<String> urls
	//@DynamicParameter(names = ["-v", "--verbose"], description = "Dynamic parameters go here")
	//Map<String, String> verbosity = new HashMap<>();
	//@Parameter(names = ["-v", "--verbose"], description = "increase verbosity", variableArity = true)
	//List<Integer> verbosity
	//@Parameter(names = "--help", help = true)
	//private boolean help
}

final Logger log = LoggerFactory.getLogger(Script.class);
log.debug "test debug ..."
def root = org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
root.setLevel(Level.ALL);
log.debug "test debug ..."
log.debug "this.args = ${this.args}"

def args = new Args();
new CommandLine(args).parse(this.args)
log.debug "args = ${args}"

//new JCommander(args, this.args)


/*
def builder = JCommander.newBuilder().addObject(args)
new Args().with {
	//.addObject(it).build().parse(argv)
}
*/
/*
@Slf4j
public class Main {
	public static main(String[] argv) {
		log.debug "starting ..."
	}
}
*/

/*
@Slf4j
class Main extends Script {
	def run() {
		log.debug "test debug ..."
		def root = org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
		root.setLevel(Level.ALL);
		log.debug "test debug ..."
		Args args = new Args();
		//JCommander.newBuilder().addObject(args).build().parse(this.args)
	}
}
*/

