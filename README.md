# noteNFC

similar to the previously available touchanote app (no relation)
written from scratch, accomplishes the basic functionality of allowing you to share a note with this app,
a link to which is then written to an NFC tag.  You can then scan the NFC tag and immediately have the note
popup in evernote or joplin.

Write NFC Tag:
- inside of evernote - select note
- share note from inside evernote to noteNFC launches noteNFC app
- noteNFC app presents write dialog to user
- user moves NFC phone close to NFC tag
- phone writes NFC Tag with evernote link

Read NFC Tag:
- user scans NFC tag with phone
- tag is recognized as an evernote url
- android OS launches evernote app with deep link for document
