# CryptoUtils
[![Github CI](https://github.com/isKONSTANTIN/CryptoUtils/actions/workflows/gradle.yml/badge.svg)](https://github.com/isKONSTANTIN/CryptoUtils/actions/workflows/gradle.yml)

Terminal program for simple seed generation, encryption, decryption, backup and more.

<pre>cu&gt; gpg --output seed.gpg --recipient bob@example.com -e seed.txt

cu&gt; ls
CryptoUtils-0.2.1.jar
seed.gpg
seed.txt

cu&gt; shamir split 3 2 seed.gpg
Done!
cu&gt; hex encode seed.shp-1 seed.shp-1.hex
Done!
cu&gt; cat seed.shp-1.hex
CE40898B7ECB74EE31FAADD192064B1DD501D8CBC17A74341241A62D11CF4F6B75E6916CE281CE5911C2E5278A4C5CC
177DDB77F9931FB19FACA0F30FDA00E3090D84CBA3B4C6B1CE62915DC1F5D88B66C332B29CCAAB7BC3495C2EADA05D9

...

cu&gt; qr generate seed-2-qr.png 500 l f:seed.shp-2
Done
cu&gt; seed_to_base satoshi like gold
<span style="color:#06989A">go</span>at      <span style="color:#06989A">go</span>ddess   <span style="background-color:#FFFFFF"><span style="color:#1C1C1C">gold   </span></span>   <span style="color:#06989A">go</span>od      <span style="color:#06989A">go</span>ose     <span style="color:#06989A">go</span>rilla   <span style="color:#06989A">go</span>spel    <span style="color:#06989A">go</span>ssip    <span style="color:#06989A">go</span>vern    <span style="color:#06989A">go</span>wn</pre>

## Wiki

Instructions for building, a list of all commands and the use of some can be found in the [Wiki](https://github.com/isKONSTANTIN/CryptoUtils/wiki/Using)

## This Tool Is Not Production Ready

Be sure to double-check the correctness of the recovery and the overall operation of the program. 