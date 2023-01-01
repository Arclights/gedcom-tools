package com.arclights

import org.slf4j.LoggerFactory

interface Logging
fun <T:Logging> T.logger() = LoggerFactory.getLogger(javaClass)