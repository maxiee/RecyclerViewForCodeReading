# Introduction

The source code of RecyclerView in Android support library is a bit hard for reading:

- It hold almost all code of RecyclerView in a single file, which contains more than 20k+ lines.
- The relation of classes is hard to recognize

So I rearrange the source, splitting the classes into separate files, make it better for code reading.

# How to contribute

## Translate comments into Chinese

Though the source of RecyclerView is well documented, it's a bit difficult for Chinese programmers to understand.

So I want translate the comments into Chinese, but keep the original English comment as well.

You can send PR to add or edit comments.

## $HTT$

There are many comments that I can't understand. My English is not very good.

So, when I meet a sentence that I can't understand, A `$HTT$` label is add.

You can help search `$HTT$` label through the project, translating the sentence into Chinese and then send a PR.

## Translation Terminology

|English|Chinese|
|---|---|
|attached|附着|
|adapter|adapter (不译)|
|cache|缓存|
|children|子元素|
|detached|已分离|
|item views|项目视图|
|pre-layout|pre-layout (不译)|
|removal|移除|
|scrapped|已销毁|
|unfiltered|未过滤|
|view group| view group (不译)|

# License

I just rearrange the source, make almost nothing new to it.

So this project follows the license of Android support library.



