package com.catalystmonitor.client.core

class BuilderMissingFieldException(fieldName: String, setterName: String) :
    RuntimeException("$fieldName was not set. Please call $setterName with a non-null value")
