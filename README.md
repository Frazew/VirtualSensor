# VirtualSensor
VirtualSensor is an Xposed module that aims at creating a virtual gyroscope in Android. It does so by using values from both the accelerometer and the compass thus deducing the angular rate on the 3 axis.

## How to use
It's pretty straightforward, you basically just install the module and activate it. Once it's there, it just does its job. There is no (yet?) further configuration to be done.

## Bug report
I only tested this on my device, which is a Moto G 2015 (osprey) running CM13 so if it doesn't work for you, I'd be glad to help and fix it.
However, if you want to help by reporting bugs, please know that without a logcat, it is generally quite hard to understand what's going on.

## License
Please see the file LICENSE for more details. VirtualSensor is basically under the LGPL license.
