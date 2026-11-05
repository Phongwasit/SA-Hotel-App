$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'tools\Local-Database.ps1')
. (Join-Path $PSScriptRoot 'tools\Invoke-Maven.ps1')
$settings = Start-LocalDatabase
$previousPassword = $env:HOTEL_DB_PASSWORD
$previousUrl = $env:HOTEL_TEST_DB_URL
Push-Location $PSScriptRoot
try {
    $env:HOTEL_DB_PASSWORD = $settings.appPassword
    $env:HOTEL_TEST_DB_URL = "jdbc:mysql://127.0.0.1:$($settings.port)/hotel_frontdesk_test?connectionTimeZone=%2B07:00&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true"
    # Dedicated Maven JVM + separate test database; no extra Surefire process
    # on this Windows computer, where nested fork startup can fail after tests.
    Invoke-HotelMaven -Goals @('-DforkCount=0','test')
} finally {
    $env:HOTEL_DB_PASSWORD = $previousPassword
    $env:HOTEL_TEST_DB_URL = $previousUrl
    Pop-Location
}
