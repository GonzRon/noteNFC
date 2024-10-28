# noteNFC
** evernote support removed

similar to the previously available touchanote app (no relation)
written from scratch, accomplishes the basic functionality of allowing you to share a note with this app,
a link to which is then written to an NFC tag.  You can then scan the NFC tag and immediately have the note
popup in joplin.

Write NFC Tag:
- inside of joplin, select note
- context menu for note, select copy external link
- share link with noteNFC app
- noteNFC app presents write dialog to user
- user scans NFC tag with phone
- phone writes NFC Tag with joplin note link

Read NFC Tag:
- user scans NFC tag with phone
- tag is recognized as an Joplin Note Link
- android OS launches joplin app with deep link for document, opening note directly

