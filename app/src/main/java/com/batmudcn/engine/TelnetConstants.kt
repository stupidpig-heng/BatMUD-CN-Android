package com.batmudcn.engine

import com.batmudcn.util.Constants

/**
 * Telnet protocol constants and negotiation helper.
 * Mirrors parser.py TelnetConstants class.
 */
object TelnetConstants {
    val IAC = Constants.IAC
    val DONT = Constants.DONT
    val DO = Constants.DO
    val WONT = Constants.WONT
    val WILL = Constants.WILL
    val SB = Constants.SB
    val SE = Constants.SE

    val COMPRESS = Constants.TELOPT_COMPRESS
    val COMPRESS2 = Constants.TELOPT_COMPRESS2
    val ECHO = Constants.TELOPT_ECHO
    val SGA = Constants.TELOPT_SGA
    val TTYPE = Constants.TELOPT_TTYPE
    val NAWS = Constants.TELOPT_NAWS
    val GMCP = Constants.TELOPT_GMCP
    val MSSP = Constants.TELOPT_MSSP
    val EOR = Constants.TELOPT_EOR
    val CHARSET = Constants.TELOPT_CHARSET

    /** Set of compress options to filter */
    val COMPRESS_OPTIONS: Set<Byte> = setOf(COMPRESS, COMPRESS2)
}
