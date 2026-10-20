# Crisis wording: review sheet for a counsellor or clinician

MindTrack is not a clinical service. When a person's message or journal note contains certain wording, the
app shows fixed helpline information instead of relying on the AI's reply. Please review the points below
and mark anything that should change.

## 1. Phrases that trigger the safety message (English)
Source: `CrisisSupport.phrases` in `app/src/main/java/com/mindtrack/app/Safety.kt`

kill myself, killing myself, end my life, ending my life, take my own life, want to die, wanna die,
wish i was dead, wish i were dead, better off dead, suicide, suicidal, self harm, self-harm, selfharm,
hurt myself, harm myself, cut myself, cutting myself, don't want to live, dont want to live,
do not want to live, no reason to live, not want to be alive, end it all, overdose on, can't go on,
cant go on, no point in living

Questions: Are any of these too broad (false alarms) or are important phrases missing? Hindi and other
languages are not covered yet; please suggest phrases for each language we add.

## 2. The message shown in the companion
> I'm really glad you told me, and I'm concerned about how much pain you may be in. You don't have to carry this alone. If you might act on these thoughts, or you are in immediate danger, please contact your local emergency number now (112 in India, 911 in the US, 999 in the UK).
> You can also talk to someone right now: India: Tele-MANAS 14416 · US & Canada: call or text 988 · UK & Ireland: Samaritans 116 123 · Elsewhere: findahelpline.com
> If there's someone you trust nearby, please consider telling them how you feel. I'm still here and happy to keep talking.

Questions: Is the tone appropriate? Should it ask directly about intent? Is "I'm still here and happy to keep talking" safe?

## 3. Helplines listed (please verify they are current and free)
India: Tele-MANAS 14416 and emergency 112. US/Canada: 988. UK/Ireland: Samaritans 116 123. Other: findahelpline.com.

## 4. When it triggers
On every companion message and on every saved journal note. The AI is still allowed to reply after the
fixed message. Ordinary low mood does not trigger it.
