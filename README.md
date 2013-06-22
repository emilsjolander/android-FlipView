FlipView
=========

About
-----

This library is made to be very easy to use and at the same time be feature complete.
With only a few lines of code you can have a flipping animation between your views, 
this looks and acts very much like the Flipboard application (I am however not affiliated with them in any way).

All flipping animations should be very smooth and i have added lighting effects so the flipping look more realistic.

Honeycomb (sdk 11) or above is required for this library to work properly, however it will compile (and run, though without good performance) for much lower versions with just a few tweaks.

Download a compiled version of the apk here:
https://www.dropbox.com/s/y4pukhdr3a3ugj8/FlipViewSample.apk


Usage
-----

First thing you have to do is download the library and add it as a library project to your project.

Next you should create a layout file container a something similar to this:
```xml
<se.emilsjolander.flipview.FlipView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:flipview="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:id="@+id/flip_view"
    flipview:orientation="vertical"
    flipview:overFlipMode="glow" />
```

In your activity/fragment you should do somthing like this:
```java
FlipView flipView = (FlipView) findViewById(R.id.flip_view);
MyAdapter adapter = new MyAdapter();
flipView.setAdapter(adapter);
```

```MyAdapter```in this case is just a normal adapter, nothing different from on you would use with a ```ListView```.


Api
---

I have designed the api to be as similar as possible to that of a ```ListView```.

FlipView uses a regular ```ListAdapter```, get and set the adapter with the following methods:
```java
void setAdapter(ListAdapter adapter);
ListAdapter getAdapter();
```

Use the following methods to get the number of pages and what the current visible page is.
```java
int getPageCount();
int getCurrentPage();
```

The following methods work like ```scrollTo```, ```smoothScrollTo```, ```scrollBy``` and ```smoothScrollBy```  from ```ListView```.
```java
void flipTo(int page);
void smoothFlipTo(int page);
void flipBy(int pageDelta);
void smoothFlipBy(int pageDelta);
```

Peaking is a way to inform the user that there is more content, or to teach the user how to interact with your application.
Peaking can be done either once or until the ```FlipView``` has been interacted with.
```java
void peakNext(boolean once);
void peakPrevious(boolean once);
```

```FlipView``` supports both vertical (default) and horizontal flipping. 
I feel it would be wrong to change the orientation dynamically so i have limited it to being set via xml.
```java
boolean isFlippingVertically();
```

This is how to set a listener on the ```FlipView``` to recieve callbacks.
```java
void setOnFlipListener(OnFlipListener onFlipListener);
```

There are multiple over flip modes. The 2 that exists now are `GLOW` and `RUBBER_BAND`. `GLOW` is using the edge glow effect seen in all android lists and is the default over flip mode. `RUBBER_BAND` is more like the iOS way to inform of the end of lists, this is also a lot like the flipboard app informs users that they are on the first/last page.
This can be set in xml using the following attribute. The mode can be either "glow" or "rubber_band".
```xml
flipview:overFlipMode="glow"
```
Here are the corrosponding java method calls. The OverFlipMode enum contains GLOW and RUBBER_BAND values.
```java
void setOverFlipMode(OverFlipMode overFlipMode);
```

There is also a listener for detecting over flip. This is usefull when wanting to implement pull-to-refresh functionality or just detecting that the user is intressted in seeing more data.
```java
void setOnOverFlipListener(OnOverFlipListener onOverFlipListener);
```


Contributing
------------

Pull requests and issues are very welcome!

Feature request are also welcome but i can't make any promise that they will make it in.
I would like to keep the library as general as possible, if you are unsure you can just ask before you code ;)


License
-------

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
