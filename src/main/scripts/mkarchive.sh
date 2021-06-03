#!/bin/zsh

JREHOME='/Users/brucejohnson/Development/mkjre'
jversion='jdk-11.0.9.1+1-jre'
jversion='jdk-16.0.51-jre'
PRGSCRIPT=ringnmr-gui

dir=`pwd`
PRG="$(basename $dir)"

if [ -e "installers" ]
then
    rm -rf installers
fi

#for os in "linux-amd64"
for os in "macosx-amd64" "linux-amd64" "windows-amd64"
do
    jreFileName=${jversion}_${os}
    echo $jreFileName

    dir=installers/$os
    if [ -e $dir ]
    then
         rm -rf $dir
    fi

    mkdir -p $dir
    cd $dir
    cp -r -p ../../target/${PRG}-*-bin/${PRG}* .
    sdir=`ls -d ${PRG}-*`
    cd $sdir
    echo $sdir

    rm lib/javafx*

    if [[ $os == "linux-amd64" ]]
    then
        rm lib/*-mac*
        rm lib/*-win*
    fi

    if [[ $os == "windows-amd64" ]]
    then
        rm lib/*-linux*
        rm lib/*-mac*
    fi

    if [[ $os == "macosx-amd64" ]]
    then
        cp -R -p ${JREHOME}/$jreFileName .
        rm lib/*-linux*
        rm lib/*-win*
    else
        cp -r -p ${JREHOME}/$jreFileName jre
    fi
    cd ..

    fname=`echo $sdir | tr '.' '_'`
    if [[ $os == "linux-amd64" ]]
    then
        tar czf ${fname}_${os}.tar.gz $sdir
    elif [[ $os == "macosx-amd64" ]]
    then
        tar czf ${fname}_${os}.tar.gz $sdir
    else
        zip -r ${fname}_${os}.zip $sdir
    fi
    cd ../..
done
