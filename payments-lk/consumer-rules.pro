# Consumer R8 rules for the Payments.lk Android SDK.
#
# The SDK uses no reflection, serialization or JNI, so an app that minifies
# needs no keep rules for it: R8 keeps whatever the app actually calls.
# This file exists so rules can be added here, and shipped inside the AAR,
# if that ever changes.
