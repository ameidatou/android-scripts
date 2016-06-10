#!/usr/bin/env groovy
/**
 * Created by dhelleberg on 24/09/14.
 * Improve command line parsing
 */
import static java.util.Calendar.HOUR_OF_DAY
import static java.util.Calendar.MINUTE
import static java.util.Calendar.SECOND


gfx_command_map = ['on' : 'visual_bars', 'off' : 'false', 'lines' : 'visual_lines']
layout_command_map = ['on' : 'true', 'off' : 'false']
overdraw_command_map = ['on' : 'show',  'off' : 'false', 'deut' : 'show_deuteranomaly']
overdraw_command_map_preKitKat = ['on' : 'true',  'off' : 'false']
show_updates_map = ['on' : '0',  'off' : '1']
now_command_map = ['' : '']
tomorrow_command_map = ['[hh:mm:ss]' : '']


command_map = ['gfx' : gfx_command_map,
               'layout' : layout_command_map,
               'overdraw' : overdraw_command_map,
               'updates' : show_updates_map,
               'now' : now_command_map,
               'tomorrow' : tomorrow_command_map]

verbose = false

def cli = new CliBuilder(usage:'devtools.groovy command option')
cli.with {
    v longOpt: 'verbose', 'prints additional output'
}
def opts = cli.parse(args)
if(!opts)
    printHelp("not provided correct option")
if(opts.arguments().size() > 2)
    printHelp("you need to provide at max two arguments: command and option")
if(opts.v)
    verbose = true

//get args
String command = opts.arguments().get(0)
String option
if (opts.arguments().size() == 2) {
    option = opts.arguments().get(1)
}

if (verbose)
    println "command: ${command}; option: ${option}."

//get adb exec
adbExec = getAdbPath();

//check connected devices
def adbDevicesCmd = "$adbExec devices"
def proc = adbDevicesCmd.execute()
proc.waitFor()

def foundDevice = false
deviceIds = []

proc.in.text.eachLine { //start at line 1 and check for a connected device
        line, number ->
            if(number > 0 && line.contains("device")) {
                foundDevice = true
                //grep out device ids
                def values = line.split('\\t')
                if(verbose)
                    println("found id: "+values[0])
                deviceIds.add(values[0])
            }
}

if(!foundDevice) {
    println("No usb devices")
    System.exit(-1)
}


def adbcmd = ""
switch ( command ) {
    case "gfx" :
        if (!option) {
            printHelp("${command}: you need to provide two arguments: command and option")
        }
        adbcmd = "shell setprop debug.hwui.profile "+gfx_command_map[option]
        executeADBCommand(adbcmd)
        break
    case "layout" :
        if (!option) {
            printHelp("${command}: you need to provide two arguments: command and option")
        }
        adbcmd = "shell setprop debug.layout "+layout_command_map[option]
        executeADBCommand(adbcmd)
        break
    case "overdraw" :
        if (!option) {
            printHelp("${command}: you need to provide two arguments: command and option")
        }
        //tricky, properties have changed over time
        adbcmd = "shell setprop debug.hwui.overdraw "+overdraw_command_map[option]
        executeADBCommand(adbcmd)
        adbcmd = "shell setprop debug.hwui.show_overdraw "+overdraw_command_map_preKitKat[option]
        executeADBCommand(adbcmd)
        break
    case "updates":
        if (!option) {
            printHelp("${command}: you need to provide two arguments: command and option")
        }
        adbcmd = "shell service call SurfaceFlinger 1002 android.ui.ISurfaceComposer"+show_updates_map[option]
        executeADBCommand(adbcmd)
        break
    case "now":
        adbcmd = "shell "+cmdNow(opts.arguments())
        executeADBCommand(adbcmd)
        break
    case "tomorrow":
        adbcmd = "shell "+cmdTomorrow(opts.arguments())
        executeADBCommand(adbcmd)
        break
    default:
        printHelp("could not find the command $command you provided")

}



kickSystemService()

System.exit(0)






void kickSystemService() {
    def proc
    int SYSPROPS_TRANSACTION = 1599295570 // ('_'<<24)|('S'<<16)|('P'<<8)|'R'

    def pingService = "shell service call activity $SYSPROPS_TRANSACTION"
    executeADBCommand(pingService)
}

void executeADBCommand(String adbcmd) {
    if(deviceIds.size == 0) {
        println("no devices connected")
        System.exit(-1)
    }
    deviceIds.each { deviceId ->
        def proc
        def adbConnect = "$adbExec -s $deviceId $adbcmd"
        if(verbose)
            println("Executing $adbConnect")
        proc = adbConnect.execute()
        proc.waitFor()
    }
}

String getAdbPath() {
    def adbExec = "adb"
    if(isWindows())
        adbExec = adbExec+".exe"
    try {
        def command = "$adbExec"    //try it plain from the path
        command.execute()
        if(verbose)
            println("using adb in "+adbExec)
        return adbExec
    }
    catch (IOException e) {
        //next try with Android Home
        def env = System.getenv("ANDROID_HOME")
        if(verbose)
            println("adb not in path trying Android home")
        if (env != null && env.length() > 0) {
            //try it here
            try {
                adbExec = env + File.separator + "platform-tools" + File.separator + "adb"
                if(isWindows())
                    adbExec = adbExec+".exe"

                def command = "$adbExec"// is actually a string
                command.execute()
                if(verbose)
                    println("using adb in "+adbExec)

                return adbExec
            }
            catch (IOException ex) {
                println("Could not find $adbExec in path and no ANDROID_HOME is set :(")
                System.exit(-1)
            }
        }
        println("Could not find $adbExec in path and no ANDROID_HOME is set :(")
        System.exit(-1)
    }
}

boolean isWindows() {
    return (System.properties['os.name'].toLowerCase().contains('windows'))
}

void printHelp(String additionalmessage) {
    println("usage: devtools.groovy [-v] command option")
    print("command: ")
    command_map.each { command, options ->
        print("\n  $command -> ")
        options.each {
            option, internal_cmd -> print("$option ")
        }
    }
    println()
    println("Error $additionalmessage")
    println()

    System.exit(-1)
}

String cmdNow(args) {
    /* sanitize argument list */
    if (args.size() != 1) {
        printHelp('now command does not accept arguments')
    }

    /* construct the command string to return */
    def now = new Date()
    def str_now = now.format("yyyyMMdd.HHmmss")

    "date -s ${str_now}"
}

String cmdTomorrow(args) {
    def str_time

    /* sanitize argument list */
    if (args.size() > 2) {
        printHelp('tomorrow command could accept one optional argument')
    }
    else if (args.size() == 2) {
        /* get optional argument time */
        str_time = args.get(1)
    }

    def now = new Date()
    def tomorrow = now.next()

    if (str_time) {
        /* parse the time entered as argument */
        def tomorrow_time = Date.parse("H:m:s", str_time)
        tomorrow.set(hourOfDay: tomorrow_time[HOUR_OF_DAY])
        tomorrow.set(minute: tomorrow_time[MINUTE])
        tomorrow.set(second: tomorrow_time[SECOND])
    }

    def str_tomorrow = tomorrow.format("yyyyMMdd.HHmmss")

    "date -s ${str_tomorrow}"
}
